package me.peterbecich.bannodemo.twitter.stats

import cats._
import cats.effect.{IO, Sync}
import cats.implicits._
import cats.syntax.all._
import com.danielasfregola.twitter4s.entities.Tweet
import fs2.async.immutable.{Signal => ISignal}
import fs2.async.mutable.Signal
import fs2.{Stream, Pipe, Scheduler}
import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, ZoneOffset, Duration}
import me.peterbecich.bannodemo.twitter.TwitterStats.getTweetTime
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object TwitterAverage {

  /*
   Interval of average recalculation
   */

  val calculationInterval = 1.second

  val second: Duration = Duration.ofSeconds(1)
  val minute: Duration = Duration.ofMinutes(1)
  val hour: Duration = Duration.ofHours(1)

  /*
   `TimeTable` is a hashmap of the tweet counts 
   in the prior 3600 seconds
   */
  type TimeTable = TrieMap[LocalDateTime, Long]
  /*
   It is stored in a `Signal`
   */
  type TimeTableSignal = Signal[IO, TimeTable]

  /*
   For a given interval of time (second, minute, hour),
   an instance of CountAccumulator holds the Tweet count
   and count of time units that have passed.
   */
  trait CountAccumulator {
    val sum: Long
    val count: Long
    val duration: Duration
    lazy val seconds: Long = duration.get(ChronoUnit.SECONDS)
    def average: Double = sum.toDouble / count
    def add(s: Long): CountAccumulator
    val ts: LocalDateTime
  }

  /*
   Each of these three instances is replaced
   with every Tweet counted.
   */
  case class SecondCountAccumulator(
    sum: Long = 0,
    count: Long = 0,
    ts: LocalDateTime
  ) extends CountAccumulator {
    val duration = second
    def add(s: Long): SecondCountAccumulator =
      this.copy(sum+s, count+1, ts = LocalDateTime.now())
  }
  case class MinuteCountAccumulator(
    sum: Long = 0,
    count: Long = 0,
    ts: LocalDateTime
  ) extends CountAccumulator {
    val duration = minute
    def add(s: Long): MinuteCountAccumulator =
      this.copy(sum+s, count+1, ts = LocalDateTime.now())
  }
  case class HourCountAccumulator(
    sum: Long = 0,
    count: Long = 0,
    ts: LocalDateTime
  ) extends CountAccumulator {
    val duration = hour
    def add(s: Long): HourCountAccumulator =
      this.copy(sum+s, count+1, ts = LocalDateTime.now())
  }

  object JSON {
    import io.circe._
    import io.circe.Encoder
    import io.circe.syntax._
    import io.circe.literal._
    import io.circe.generic.semiauto._

    import me.peterbecich.bannodemo.JSON.Common._

    implicit val averagePayloadEncoder: Encoder[AveragePayload] =
      deriveEncoder

    /*
     DTO to be easily serialized to JSON.

     Each average -- by hour, minute, second -- is recalculated
     on a frequent interval by an asynchronous process.

     If this process were to stop for some reason,
     the timestamp of that average would begin to lag,
     indicating the problem to the front-end.

     */
    case class AveragePayload(
      name: String,
      secondAverage: Double,
      secondTimestamp: LocalDateTime,
      minuteAverage: Double,
      minuteTimestamp: LocalDateTime,
      hourAverage: Double,
      hourTimestamp: LocalDateTime
    )

    def makeAveragePayload(
      name: String,
      secondCountAcc: SecondCountAccumulator,
      minuteCountAcc: MinuteCountAccumulator,
      hourCountAcc: HourCountAccumulator
    ): AveragePayload =
      AveragePayload(name,
        secondCountAcc.average, secondCountAcc.ts,
        minuteCountAcc.average, minuteCountAcc.ts,
        hourCountAcc.average, hourCountAcc.ts
      )
  }

  /*
   A `TimeTableSignal` is constructed, containing an 
   empty `HashMap`.
   */
  private def makeTimeTableSignal: IO[TimeTableSignal] =
    Signal.apply(TrieMap.empty[LocalDateTime, Long])(IO.ioEffect, global)

  def makeAverage(_name: String, _predicate: Tweet => Boolean):
      IO[TwitterAverage] = for {
    _timeTableSignal <- makeTimeTableSignal
    _secondCountAccumulatorSignal <- Signal.apply {
      SecondCountAccumulator(ts = LocalDateTime.now())}(IO.ioEffect, global)
    _minuteCountAccumulatorSignal <- Signal.apply {
      MinuteCountAccumulator(ts = LocalDateTime.now())}(IO.ioEffect, global)
    _hourCountAccumulatorSignal <- Signal.apply {
      HourCountAccumulator(ts = LocalDateTime.now())}(IO.ioEffect, global)
  } yield new TwitterAverage {
      val name = _name
      val timeTableSignal = _timeTableSignal
      val predicate = _predicate
      val secondCountAccumulatorSignal = _secondCountAccumulatorSignal
      val minuteCountAccumulatorSignal = _minuteCountAccumulatorSignal
      val hourCountAccumulatorSignal = _hourCountAccumulatorSignal
     }

  /*
   Construct the signals that will contain the 
   hour, minute and second averages.
   Construct a `TwitterAverage`.

   */
  private def _makeAverage(_name: String, _predicate: Tweet => Boolean):
      IO[(TwitterAverage, TimeTableSignal)] = for {
    _timeTableSignal <- makeTimeTableSignal
    _secondCountAccumulatorSignal <- Signal.apply {
      SecondCountAccumulator(ts = LocalDateTime.now())}(IO.ioEffect, global)
    _minuteCountAccumulatorSignal <- Signal.apply {
      MinuteCountAccumulator(ts = LocalDateTime.now())}(IO.ioEffect, global)
    _hourCountAccumulatorSignal <- Signal.apply {
      HourCountAccumulator(ts = LocalDateTime.now())}(IO.ioEffect, global)
  } yield (new TwitterAverage {
      val name = _name
      val timeTableSignal = _timeTableSignal
      val predicate = _predicate
      val secondCountAccumulatorSignal = _secondCountAccumulatorSignal
      val minuteCountAccumulatorSignal = _minuteCountAccumulatorSignal
      val hourCountAccumulatorSignal = _hourCountAccumulatorSignal
  }, _timeTableSignal)

  
}

/*
 `TwitterAverage` calculates the average rate of Tweets
 that satisfy a given predicate
 over a unit of time -- hour, minute or second.

 It provides a rolling average, recalculated at the interval
 specified in `calculationInterval`.

 The calculation interval is set to 1 second.  
 This means the average Tweets per hour is recalculated 
 3600 times per hour.

 */

abstract class TwitterAverage {

  import TwitterAverage._

  val name: String
  val timeTableSignal: TimeTableSignal
  val predicate: Tweet => Boolean

  // Second, minute and hour averages will be calculated every second

  // Tweet counts for every second; count added every second
  val secondCountAccumulatorSignal: Signal[IO, SecondCountAccumulator]
  // Tweet counts for every minute; count added every second
  val minuteCountAccumulatorSignal: Signal[IO, MinuteCountAccumulator]
  // Tweet counts for every hour; count added every second
  val hourCountAccumulatorSignal: Signal[IO, HourCountAccumulator]

  lazy val averagePayloadStream: Stream[IO, JSON.AveragePayload] = {
    lazy val secondStream: Stream[IO, SecondCountAccumulator] =
      secondCountAccumulatorSignal.continuous.flatMap { acc =>
        Stream.emit(acc)
      }
    lazy val minuteStream: Stream[IO, MinuteCountAccumulator] =
      minuteCountAccumulatorSignal.continuous.flatMap { acc =>
        Stream.emit(acc)
      }
    lazy val hourStream: Stream[IO, HourCountAccumulator] =
      hourCountAccumulatorSignal.continuous.flatMap { acc =>
        Stream.emit(acc)
      }

    lazy val zippedStreams: Stream[IO, ((SecondCountAccumulator, MinuteCountAccumulator), HourCountAccumulator)] =
      secondStream.zip(minuteStream).zip(hourStream)

    lazy val _zippedStreams: Stream[IO, (SecondCountAccumulator, MinuteCountAccumulator, HourCountAccumulator)] =
      zippedStreams.map { case ((second, minute), hour) => (second, minute, hour) }

    lazy val payloadStream: Stream[IO, JSON.AveragePayload] =
      _zippedStreams.map { case (second, minute, hour) =>
        JSON.makeAveragePayload(name, second, minute, hour)
      }

    payloadStream
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
        ()
      }
    }

  // Remove timestamps from time table if they are beyond a certain age, in seconds.
  // This cleans the time table of old entries.
  private def filterTimeThreshold(threshold: Duration = hour): IO[Unit] =
    timeTableSignal.modify { timeTable =>
      // TODO investigate potential for lost data with concurrent calls to `modify` on Signal
      // TODO get time zones right
      // val zone = ZoneOffset.ofHours(0)
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
    if(predicate(tweet)) {
      val timestamp = getTweetTime(tweet)
      Stream.eval(incrementTime(timestamp)).flatMap { _ =>
        Stream.emit(tweet)
      }
    } else Stream.emit(tweet)
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

  private def priorSecondTimeTable: IO[TimeTable] =
    truncateTimeTable(second)

  private def priorMinuteTimeTable: IO[TimeTable] =
    truncateTimeTable(minute)

  private def priorHourTimeTable: IO[TimeTable] =
    truncateTimeTable(hour)

  // sum of tweets in the past second
  private def secondSum: IO[(Long, Long)] =
    priorSecondTimeTable.map { timeTable =>
      (timeTable.values.sum, timeTable.size)
    }

  // sum of tweets in the past minute, from this second
  private def minuteSum: IO[(Long, Long)] =
    priorMinuteTimeTable.map { timeTable =>
      (timeTable.values.sum, timeTable.size)
    }

  // sum of tweets in the past hour, from this second
  private def hourSum: IO[(Long, Long)] =
    priorHourTimeTable.map { timeTable =>
      (timeTable.values.sum, timeTable.size)
    }

  // calculates second averages
  private lazy val calculateSecondAverage: Stream[IO, Unit] =
    schedulerStream.flatMap { scheduler =>
      scheduler.fixedRate(calculationInterval)(IO.ioEffect, global).flatMap { _ =>
        Stream.eval(secondSum).flatMap { case (sum, count) =>
          Stream.eval {
            secondCountAccumulatorSignal.modify { acc =>
              acc.add(sum)
            }
          }
        }
      }
    }.drain
  
  // calculates minute averages
  private lazy val calculateMinuteAverage: Stream[IO, Unit] =
    schedulerStream.flatMap { scheduler =>
      scheduler.fixedRate(calculationInterval)(IO.ioEffect, global).flatMap { _ =>
        Stream.eval(minuteSum).flatMap { case (sum, count) =>
          Stream.eval {
            minuteCountAccumulatorSignal.modify { acc =>
              acc.add(sum)
            }
          }
        }
      }
    }.drain

  // calculates hourly averages
  private lazy val calculateHourlyAverage: Stream[IO, Unit] =
    schedulerStream.flatMap { scheduler =>
      scheduler.fixedRate(calculationInterval)(IO.ioEffect, global).flatMap { _ =>
        Stream.eval(hourSum).flatMap { case (sum, count) =>
          Stream.eval {
            hourCountAccumulatorSignal.modify { acc =>
              acc.add(sum)
            }
          }
        }
      }
    }.drain

  private lazy val schedulerStream: Stream[IO, Scheduler] = Scheduler.apply[IO](8)

  // prints Tweets/second to console every two seconds
  private lazy val recentCount: Stream[IO, (LocalDateTime, Long, Long)] =
    schedulerStream.flatMap { scheduler =>
      scheduler.fixedRate(30.second)(IO.ioEffect, global).flatMap { _ =>
        Stream.eval(timeTableSignal.get).flatMap { timeTable =>
          Stream.eval(IO(LocalDateTime.now()).map { _.truncatedTo(ChronoUnit.SECONDS) }).map { now =>
            val minus10Seconds = now.minus(Duration.ofSeconds(10))
            (minus10Seconds, timeTable.get(minus10Seconds).getOrElse(0), timeTable.size)
          }
        }
      }
    }

  // prints Tweets/second to console every two seconds
  private lazy val printRecentCount: Stream[IO, Unit] = recentCount
    .map { case (ts, count, timeTableSize) =>
      name + " " + ts.toString() + " count: " + count + " time table size: " + timeTableSize + "\n"}
    .intersperse("\n")
    .through(fs2.text.utf8Encode)
    .observe(fs2.io.stdout)
    .drain

  lazy val watchSecondSignal: Stream[IO, Unit] =
    schedulerStream.flatMap { scheduler =>
      scheduler.fixedRate(30.second)(IO.ioEffect, global).flatMap { _ =>
        Stream.eval(secondCountAccumulatorSignal.get)
      }
    }.map(acc => name + " second signal: "+acc.toString + " average: " + acc.average + "\n")
      .intersperse("\n")
      .through(fs2.text.utf8Encode)
      .through(fs2.io.stdout)
      .drain

  lazy val watchMinuteSignal: Stream[IO, Unit] =
    schedulerStream.flatMap { scheduler =>
      scheduler.fixedRate(30.second)(IO.ioEffect, global).flatMap { _ =>
        Stream.eval(minuteCountAccumulatorSignal.get)
      }
    }.map(acc => name + " minute signal: "+acc.toString + " average: " + acc.average + "\n")
      .intersperse("\n")
      .through(fs2.text.utf8Encode)
      .through(fs2.io.stdout)
      .drain

  lazy val watchHourSignal: Stream[IO, Unit] =
    schedulerStream.flatMap { scheduler =>
      scheduler.fixedRate(30.second)(IO.ioEffect, global).flatMap { _ =>
        Stream.eval(hourCountAccumulatorSignal.get)
      }
    }.map(acc => name + " hour signal: "+acc.toString + " average: " + acc.average + "\n")
      .intersperse("\n")
      .through(fs2.text.utf8Encode)
      .through(fs2.io.stdout)
      .drain
  
  //  incrementTimePipe.andThen(filterTimeThresholdPipe)
  val averagePipe: Pipe[IO, Tweet, Tweet] =
    (s: Stream[IO, Tweet]) =>
  incrementTimePipe(s)
    .through(filterTimeThresholdPipe)
    // .concurrently(printRecentCount)
    .concurrently(calculateHourlyAverage)
    .concurrently(calculateMinuteAverage)
    .concurrently(calculateSecondAverage)
    // .concurrently(watchSecondSignal)
    // .concurrently(watchMinuteSignal)
    // .concurrently(watchHourSignal)

}
