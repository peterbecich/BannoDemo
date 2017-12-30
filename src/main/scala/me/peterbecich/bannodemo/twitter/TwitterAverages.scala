package me.peterbecich.bannodemo.twitter

import cats._
import cats.implicits._
import cats.syntax.all._
import cats.effect.{IO, Sync}

import fs2._
import fs2.async.mutable.Signal

import com.danielasfregola.twitter4s.entities.Tweet

import scala.collection.concurrent.TrieMap

import java.time.{LocalDateTime, ZoneOffset, Duration}
import java.time.temporal.ChronoUnit

import me.peterbecich.bannodemo.twitter.TwitterStats.getTweetTime

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object TwitterAverages {

  val hour: Long = 60*60
  // val hour: Long = 600

  // Time Table is a hashmap of the prior 3600 seconds
  type TimeTable = TrieMap[LocalDateTime, Long]

  // abstract class TwitterAverage {
  abstract class TwitterAverage {
    val name: String
    val timeTableSignal: Signal[IO, TimeTable]
    val predicate: Tweet => Boolean

    // for the timestamp key, increment the count value
    private def incrementTime(timestamp: LocalDateTime): IO[Unit] =
      timeTableSignal.get.flatMap { timeTable =>
        IO {
          val timestampTruncated: LocalDateTime =
            timestamp.truncatedTo(ChronoUnit.SECONDS)
          val count: Long = timeTable.getOrElse(timestampTruncated, 0)
          // if(count > 0  && count % 15 == 0)
          //   println(name+" "+timestamp.toString()+" count: "+count+" time table size: "+timeTable.size)
          // TODO potential for miscount with concurrent access???
          timeTable.put(timestampTruncated, count+1)
          ()
        }
      }

    // remove timestamps from table if they are beyond a certain age, in seconds
    private def filterTimeThreshold(secondsThreshold: Long = hour): IO[Unit] =
      timeTableSignal.modify { timeTable =>
        // TODO investigate potential for lost data with concurrent calls to `modify` on Signal
        // TODO get time zones right
        val zone = ZoneOffset.ofHours(0)
        val now = LocalDateTime.now()
        def diffUnderThreshold(ts1: LocalDateTime): Boolean = {
          val diff = now.toEpochSecond(zone) - ts1.toEpochSecond(zone)
          (diff) < secondsThreshold
        }
        val timeTable2 = timeTable.filter((kv) => diffUnderThreshold(kv._1))
        timeTable2
      }.map(_ => ())

    // For every input Tweet at the head of the pipe,
    // increment count of timestamp held by the Signal.
    // Pass tweet through to tail of the pipe
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

    // For every input Tweet at head of the pipe,
    // clean up the old timestamps held by the Signal.
    // Remove all timestamps past a certain age
    private def filterTimeThresholdPipe: Pipe[IO, Tweet, Tweet] =
      (tweets: Stream[IO, Tweet]) =>
    tweets.flatMap { tweet =>
      Stream.eval(filterTimeThreshold(hour)).flatMap { _ =>
        Stream.emit(tweet)
      }
    }

    private def truncateTimeTable(secondsThreshold: Long): IO[TimeTable] =
      timeTableSignal.get.map { timeTable =>
        val zone = ZoneOffset.ofHours(0)
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
        def diffUnderThreshold(ts1: LocalDateTime): Boolean = {
          val diff = now.toEpochSecond(zone) - ts1.toEpochSecond(zone)
          (diff) < secondsThreshold
        }
        val timeTable2 = timeTable.filter((kv) => diffUnderThreshold(kv._1))
        timeTable2
      }

    private def priorHourTimeTable: IO[TimeTable] =
      truncateTimeTable(hour)

    private def priorMinuteTimeTable: IO[TimeTable] =
       truncateTimeTable(60)

    private def priorSecondTimeTable: IO[TimeTable] =
      timeTableSignal.get.map { timeTable =>
        val zone = ZoneOffset.ofHours(0)
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
        timeTable.filter((kv) => kv._1 == now)
      }

    // TODO should these be vals?
    def getHourSum: IO[Long] =
      priorHourTimeTable.map { timeTable =>
        timeTable.values.sum
      }

    def getMinuteSum: IO[Long] =
      priorMinuteTimeTable.map { timeTable =>
        timeTable.values.sum
      }

    def getSecondSum: IO[Long] =
      priorSecondTimeTable.map { timeTable =>
        timeTable.values.sum
      }

    // Returns count of Tweets for now - 10 seconds
    // and number of keys in time table
    // private def getRecentCount: IO[(LocalDateTime, Long, Long)] =
    //   IO(LocalDateTime.now()).map { _.truncatedTo(ChronoUnit.SECONDS) }.flatMap { now =>
    //     timeTableSignal.get.map { timeTable =>
    //       val minus10Seconds = now.minus(Duration.ofSeconds(10))
    //       (minus10Seconds, timeTable.get(minus10Seconds).getOrElse(0), timeTable.size)
    //     }
    //   }


    // private val printRecentCountSink: Sink[IO, Tweet] = (_: Stream[IO, Tweet]) =>
    //   Stream
    //     .repeatEval(getRecentCount)
    //     .map { case (ts, count, timeTableSize) =>
    //       name + " " + ts.toString() + " count: " + count + " time table size: " + timeTableSize}
    //     .intersperse("\n")
    //     .through(fs2.text.utf8Encode)
    //     .observe(fs2.io.stdout)
    //     .drain

    lazy val schedulerStream: Stream[IO, Scheduler] = Scheduler.apply[IO](2)

    private lazy val recentCount: Stream[IO, (LocalDateTime, Long, Long)] =
      schedulerStream.flatMap { scheduler =>
        scheduler.fixedRate(2.second)(IO.ioEffect, global).flatMap { _ =>
          Stream.eval(timeTableSignal.get).flatMap { timeTable =>
            Stream.eval(IO(LocalDateTime.now()).map { _.truncatedTo(ChronoUnit.SECONDS) }).map { now =>
              val minus10Seconds = now.minus(Duration.ofSeconds(10))
              (minus10Seconds, timeTable.get(minus10Seconds).getOrElse(0), timeTable.size)
            }
          }
        }
      }


    lazy val printRecentCount: Stream[IO, Unit] = recentCount
        .map { case (ts, count, timeTableSize) =>
          name + " " + ts.toString() + " count: " + count + " time table size: " + timeTableSize + "\n"}
        .intersperse("\n")
        .through(fs2.text.utf8Encode)
        .observe(fs2.io.stdout)
        .drain
    
    //  incrementTimePipe.andThen(filterTimeThresholdPipe)
    val averagePipe: Pipe[IO, Tweet, Tweet] =
      (s: Stream[IO, Tweet]) =>
    incrementTimePipe(s)
      .through(filterTimeThresholdPipe)
      .concurrently(printRecentCount)

  }

  private def createTimeTableSignal: IO[Signal[IO, TimeTable]] =
    Signal.apply(TrieMap.empty[LocalDateTime, Long])(IO.ioEffect, global)

  private def makeAverage(_name: String, _predicate: Tweet => Boolean): IO[TwitterAverage] =
    createTimeTableSignal.map { timeTableSignal_ =>
      new TwitterAverage {
        val name = _name
        val timeTableSignal = timeTableSignal_
        val predicate = _predicate
      }
    }

  private val tweetAverage: IO[TwitterAverage] = makeAverage("TweetAverage", (_) => true)
  private val emojiAverage: IO[TwitterAverage] = makeAverage("EmojiAverage", (_) => true)

  val makeAverages: IO[List[TwitterAverage]] =
    Traverse[List].sequence(List(tweetAverage, emojiAverage))

  private def passThru[A]: Pipe[IO, A, A] = stream => stream

  private def pipeConcatenationMonoid[A] = new Monoid[Pipe[IO, A, A]] {
    def combine(pipe1: Pipe[IO, A, A], pipe2: Pipe[IO, A, A]): Pipe[IO, A, A] =
      pipe1.andThen(pipe2)
    def empty: Pipe[IO, A, A] = passThru[A]
  }

  val makeConcatenatedAveragePipe: IO[Pipe[IO, Tweet, Tweet]] =
    makeAverages.map { averages =>
      Foldable[List].foldMap(averages)(_.averagePipe)(pipeConcatenationMonoid)
    }
  // val makeConcatenatedAveragePipe: IO[Pipe[IO, Tweet, Tweet]] =
  //   tweetAverage.map(_.averagePipe)
}

object TwitterAverageExample {
  import fs2.{io, text}

  import TwitterAverages._

  // val averageTwitter: IO[] = {for {
  //   _ <- IO(println("begin averaging streaming"))
  //   twitterStream <- TwitterQueue.createTwitterStream
  //   averagePipe <- TwitterAverages.makeConcatenatedAveragePipe
  //   _ <- IO(println("acquired Twitter stream and average pipe"))
  // } yield {
  //   twitterStream
  //     .through(averagePipe)
  //     .drain.run
  // }: IO[IO[Unit]] }

  val averageTwitter2: IO[Unit] =
    IO(println("acquire Twitter stream")).flatMap { _ =>
      TwitterQueue.createTwitterStream.flatMap { twitterStream =>
        TwitterAverages.makeConcatenatedAveragePipe.flatMap { averagePipe =>
          IO(println("acquired Twitter stream and average pipe")).flatMap { _ =>
            twitterStream
              .through(averagePipe)
              .drain
              .run
          }
        }
      }
    }

  def main(args: Array[String]): Unit = {
    println("twitter averages example")

    averageTwitter2.unsafeRunSync()
  }
 
}
