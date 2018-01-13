package me.peterbecich.bannodemo.twitter.stats

import cats._
import cats.implicits._
import cats.syntax.all._
import cats.effect.{IO, Sync}

import fs2.{Stream, Pipe, Scheduler}
import fs2.async.mutable.Signal
import fs2.async.immutable.{Signal => ISignal}

import com.danielasfregola.twitter4s.entities.Tweet

import scala.collection.concurrent.TrieMap

import java.time.{LocalDateTime, ZoneOffset, Duration}
import java.time.temporal.ChronoUnit

import me.peterbecich.bannodemo.twitter.TwitterStats.getTweetTime

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


object TwitterWindowAccumulator {

  val calculationInterval = 1.second

  // val minute: Long = 60
  // val hour: Long = minute*60
  val second: Duration = Duration.ofSeconds(1)
  val minute: Duration = Duration.ofMinutes(1)
  val hour: Duration = Duration.ofHours(1)

  // Time Table is a hashmap of the prior 3600 seconds
  type TimeTable = TrieMap[LocalDateTime, Long]
  type TimeTableSignal = Signal[IO, TimeTable]

  case class MinuteCountAccumulator(
    sum: Long = 0,
    ts: LocalDateTime = LocalDateTime.now()
  ) {
    val duration = minute
    def set(sum: Long): MinuteCountAccumulator =
      this.copy(sum, ts = LocalDateTime.now())
  }

  object JSON {
    import io.circe._
    import io.circe.Encoder
    import io.circe.syntax._
    import io.circe.literal._
    import io.circe.generic.semiauto._

    import me.peterbecich.bannodemo.JSON.Common._

    implicit val windowPayloadEncoder: Encoder[WindowAccumulatorPayload] = deriveEncoder

    case class WindowAccumulatorPayload(
      name: String,
      timestamp: LocalDateTime,
      minuteCount: Long
    )

    def makeWindowAccumulatorPayload(
      name: String,
      minuteCountAcc: MinuteCountAccumulator,
    ): WindowAccumulatorPayload =
      WindowAccumulatorPayload(name, minuteCountAcc.ts, minuteCountAcc.sum)
  }

  private def makeTimeTableSignal: IO[TimeTableSignal] =
    Signal.apply(TrieMap.empty[LocalDateTime, Long])(IO.ioEffect, global)

  def makeWindowAccumulator(watch: Boolean = false):
      IO[TwitterWindowAccumulator] = for {
    _timeTableSignal <- makeTimeTableSignal
    _minuteCountAccumulatorSignal <- Signal.apply {
      MinuteCountAccumulator(ts = LocalDateTime.now())}(IO.ioEffect, global)
  } yield {
    new TwitterWindowAccumulator(watch) {
      val timeTableSignal = _timeTableSignal
      val minuteCountAccumulatorSignal = _minuteCountAccumulatorSignal
     }
  }

}

abstract class TwitterWindowAccumulator(watch: Boolean = false) {

  import TwitterWindowAccumulator._

  val name: String = "MinuteCount"
  val timeTableSignal: TimeTableSignal

  // Tweet counts for every minute; count added every second
  val minuteCountAccumulatorSignal: Signal[IO, MinuteCountAccumulator]

  lazy val windowAccumulatorPayloadStream: Stream[IO, JSON.WindowAccumulatorPayload] =
    minuteCountAccumulatorSignal.continuous.flatMap { acc =>
      Stream.emit(JSON.makeWindowAccumulatorPayload(name, acc))
    }

  // for the timestamp key, increment the count value
  private def incrementTime(timestamp: LocalDateTime): IO[Unit] =
    timeTableSignal.get.flatMap { timeTable =>
      IO {
        val timestampTruncated: LocalDateTime =
          timestamp.truncatedTo(ChronoUnit.SECONDS)
        val count: Long = timeTable.getOrElse(timestampTruncated, 0)
        // TODO potential for miscount with concurrent access???
        timeTable.put(timestampTruncated, count+1)
        // println("count: "+count)
        ()
      }
    }

  // Remove timestamps from time table if they are beyond a certain age, in seconds.
  // This cleans the time table of old entries.
  private def filterTimeThreshold(threshold: Duration = Duration.ofMinutes(3)): IO[Unit] =
    timeTableSignal.modify { timeTable =>
      val now = LocalDateTime.now()
      val cutoff = now.minus(threshold)
      // predicate determines if timestamp is younger than durationThreshold
      def underThreshold(ts: LocalDateTime): Boolean =
        ts.isAfter(cutoff)
      timeTable.filter { kv => underThreshold(kv._1) }
    }.map(_ => ())

  private def incrementTimePipe: Pipe[IO, Tweet, Tweet] =
    (tweetInput: Stream[IO, Tweet]) =>
  tweetInput.flatMap { tweet =>
      val timestamp = getTweetTime(tweet)
      Stream.eval(incrementTime(timestamp)).flatMap { _ =>
        Stream.emit(tweet)
      }
  }

  private def filterTimeThresholdPipe: Pipe[IO, Tweet, Tweet] =
    (tweets: Stream[IO, Tweet]) =>
  tweets.flatMap { tweet =>
    Stream.eval(filterTimeThreshold(hour)).flatMap { _ =>
      Stream.emit(tweet)
    }
  }

  private def truncateTimeTable
    (threshold: Duration, shift: Duration = Duration.ofSeconds(5)): IO[TimeTable] =
    timeTableSignal.get.map { timeTable =>
      // val zone = ZoneOffset.ofHours(0)
      val now = LocalDateTime.now()
      val upperCutoff = now.minus(shift)
      val lowerCutoff = now.minus(threshold).minus(shift)
      // predicate determines if timestamp is younger than durationThreshold
      def underThreshold(ts: LocalDateTime): Boolean =
        ts.isEqual(lowerCutoff) || (ts.isAfter(lowerCutoff) && ts.isBefore(upperCutoff))
      timeTable.filter { kv => underThreshold(kv._1) }
    }

  private def priorMinuteTimeTable: IO[TimeTable] =
    truncateTimeTable(minute)

  // sum of tweets in the past minute, from this second
  private def minuteSum: IO[Long] =
    priorMinuteTimeTable.map { timeTable =>
      timeTable.values.sum
    }
  
  // calculates minute averages
  private lazy val calculateMinuteCount: Stream[IO, Unit] =
    schedulerStream.flatMap { scheduler =>
      scheduler.fixedRate(calculationInterval)(IO.ioEffect, global).flatMap { _ =>
        Stream.eval(minuteSum).flatMap { sum =>
          Stream.eval {
            minuteCountAccumulatorSignal.modify { acc =>
              // println("sum: "+sum)
              // println("acc: "+acc)
              acc.set(sum)
            }
          }
        }
      }
    }.drain

  private lazy val schedulerStream: Stream[IO, Scheduler] = Scheduler.apply[IO](8)

  // prints Tweets/second to console every two seconds
  private lazy val recentCount: Stream[IO, (LocalDateTime, Long)] =
    schedulerStream.flatMap { scheduler =>
      scheduler.fixedRate(10.second)(IO.ioEffect, global).flatMap { _ =>
        Stream.eval {
          minuteCountAccumulatorSignal.get.map { minuteCountAcc =>
            (minuteCountAcc.ts, minuteCountAcc.sum)
          }
        }
      }
    }

  // prints Tweets/second to console every two seconds
  private lazy val printRecentCount: Stream[IO, Unit] = recentCount
    .map { case (ts, sum) =>
      "tweet count in past minute:" + " " + ts.toString() + " " + sum + "\n"
    }
    .intersperse("\n")
    .through(fs2.text.utf8Encode)
    .observe(fs2.io.stdout)
    .drain

  lazy val streamInactive: Stream[IO, Boolean] =
    schedulerStream.flatMap { scheduler =>
      scheduler.fixedRate(30.second)(IO.ioEffect, global).flatMap { _ =>
        Stream.eval {
          minuteCountAccumulatorSignal.get.map { minuteCountAcc =>
            println("minute count acc == 0: " + (minuteCountAcc.sum == 0))
            minuteCountAcc.sum == 0
          }
        }
      }
    }


  val windowAccumulatorPipe: Pipe[IO, Tweet, Tweet] =
    if(watch)
      (s: Stream[IO, Tweet]) => incrementTimePipe(s)
        .through(filterTimeThresholdPipe)
        .concurrently(printRecentCount)
        .concurrently(calculateMinuteCount)
    else
      (s: Stream[IO, Tweet]) => incrementTimePipe(s)
        .through(filterTimeThresholdPipe)
        .concurrently(calculateMinuteCount)
}
