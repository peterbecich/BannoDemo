package me.peterbecich.bannodemo.twitter

import cats._
import cats.implicits._
import cats.syntax.all._
import cats.effect.{IO, Sync}

import fs2._
import fs2.async.mutable.Signal
import fs2.async.immutable.{Signal => ISignal}

import com.danielasfregola.twitter4s.entities.Tweet

import scala.collection.concurrent.TrieMap

import java.time.{LocalDateTime, ZoneOffset, Duration}
import java.time.temporal.ChronoUnit

import me.peterbecich.bannodemo.twitter.TwitterStats.getTweetTime

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object TwitterAverages {

  val minute: Long = 60
  val hour: Long = minute*60

  // Time Table is a hashmap of the prior 3600 seconds
  type TimeTable = TrieMap[LocalDateTime, Long]
  type TimeTableSignal = Signal[IO, TimeTable]

  // Set of Tweet counts taken from the Time Table;
  // used to produce averages
  trait CountAccumulator {
    val sum: Long
    val count: Long
    val seconds: Long
    val average: Double = sum * 1.0 / seconds
    // def incCount: CountAccumulator = this.copy(sum, count+1)
    def add(s: Long, n: Long): CountAccumulator
  }

  case class HourCountAccumulator(sum: Long = 0, count: Long = 0) extends CountAccumulator {
    val seconds = hour
    def add(s: Long, n: Long): HourCountAccumulator = this.copy(sum+s, count+n)
  }
  case class MinuteCountAccumulator(sum: Long = 0, count: Long = 0) extends CountAccumulator {
    val seconds = minute
    def add(s: Long, n: Long): MinuteCountAccumulator = this.copy(sum+s, count+n)
  }
  case class SecondCountAccumulator(sum: Long = 0, count: Long = 0) extends CountAccumulator {
    val seconds = 1
    def add(s: Long, n: Long): SecondCountAccumulator = this.copy(sum+s, count+n)
  }

  // type CountAccumulatorSignal = Signal[IO, CountAccumulator]

  // abstract class TwitterAverage {
  abstract class TwitterAverage {
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

    val secondAverageSignal: ISignal[IO, Double] =
      secondCountAccumulatorSignal.map { _.count * 1.0 }
    val minuteAverageSignal: ISignal[IO, Double] =
      minuteCountAccumulatorSignal.map { _.count * 1.0 / minute }
    val hourAverageSignal: ISignal[IO, Double] =
      hourCountAccumulatorSignal.map { _.average * 1.0 / hour }

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

    private def truncateTimeTable(secondsThreshold: Long): TimeTableSignal =
      timeTableSignal.imap { timeTable =>
        val zone = ZoneOffset.ofHours(0)
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
        def diffUnderThreshold(ts1: LocalDateTime): Boolean = {
          val diff = now.toEpochSecond(zone) - ts1.toEpochSecond(zone)
          (diff) < secondsThreshold
        }
        val timeTable2: TimeTable = timeTable.filter((kv) => diffUnderThreshold(kv._1))
        timeTable2
      }(identity)

    private lazy val priorHourTimeTable: TimeTableSignal =
      truncateTimeTable(hour)

    private lazy val priorMinuteTimeTable: TimeTableSignal =
       truncateTimeTable(60)

    private def priorSecondTimeTable: TimeTableSignal =
      timeTableSignal.imap { timeTable =>
        val zone = ZoneOffset.ofHours(0)
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
        timeTable.filter((kv) => kv._1 == now)
      }(identity)

    // sum of tweets in the past hour, from this second
    private lazy val hourSumSignal: ISignal[IO, (Long, Long)] =
      priorHourTimeTable.map { timeTable =>
        (timeTable.values.sum, timeTable.size)
      }

    // sum of tweets in the past minute, from this second
    private lazy val minuteSumSignal: ISignal[IO, (Long, Long)] =
      priorMinuteTimeTable.map { timeTable =>
        (timeTable.values.sum, timeTable.size)
      }

    // sum of tweets in the past second
    private lazy val secondSumSignal: ISignal[IO, (Long, Long)] =
      priorSecondTimeTable.map { timeTable =>
        (timeTable.values.sum, timeTable.size)
      }

    // calculates hourly averages
    private lazy val calculateHourlyAverage: Stream[IO, Unit] = 
      hourSumSignal.discrete.flatMap { case (hourSum, hourCount) =>
        Stream.eval(hourCountAccumulatorSignal.modify { countAcc =>
          val _countAcc = countAcc.add(hourSum, hourCount)
          println((_countAcc.count * 1.0 / hour) + "   " + _countAcc.count + "  hourSum: " + hourSum + "  hourCount: " + hourCount)
          _countAcc
        }).drain
      }

    // calculates minute averages
    private lazy val calculateMinuteAverage: Stream[IO, Unit] = 
      minuteSumSignal.discrete.flatMap { case (minuteSum, minuteCount) =>
        Stream.eval(minuteCountAccumulatorSignal.modify { _.add(minuteSum, minuteCount) }).drain
      }

    // calculates second averages
    private lazy val calculateSecondAverage: Stream[IO, Unit] = 
      secondSumSignal.discrete.flatMap { case (secondSum, secondCount) =>
        Stream.eval(secondCountAccumulatorSignal.modify { _.add(secondSum, secondCount) }).drain
      }
    
    

    lazy val schedulerStream: Stream[IO, Scheduler] = Scheduler.apply[IO](2)

    // prints Tweets/second to console every two seconds
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

    // prints Tweets/second to console every two seconds
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
      .concurrently(calculateHourlyAverage)
      .concurrently(calculateMinuteAverage)
      .concurrently(calculateSecondAverage)

  }

  private def createTimeTableSignal: IO[TimeTableSignal] =
    Signal.apply(TrieMap.empty[LocalDateTime, Long])(IO.ioEffect, global)

  // http://www.scala-lang.org/api/current/scala/collection/immutable/HashSet$.html
  // private def createCountAccumulatorSignal: IO[CountAccumulatorSignal] =
  //   Signal.apply(CountAccumulator(0, 0))

  private def makeAverage(_name: String, _predicate: Tweet => Boolean): IO[TwitterAverage] = for {
    _timeTableSignal <- createTimeTableSignal
    _secondCountAccumulatorSignal <- Signal.apply(SecondCountAccumulator())(IO.ioEffect, global)
    _minuteCountAccumulatorSignal <- Signal.apply(MinuteCountAccumulator())(IO.ioEffect, global)
    _hourCountAccumulatorSignal <- Signal.apply(HourCountAccumulator())(IO.ioEffect, global)
  } yield {
    new TwitterAverage {
      val name = _name
      val timeTableSignal = _timeTableSignal
      val predicate = _predicate
      val secondCountAccumulatorSignal = _secondCountAccumulatorSignal
      val minuteCountAccumulatorSignal = _minuteCountAccumulatorSignal
      val hourCountAccumulatorSignal = _hourCountAccumulatorSignal
     }
  }

  // createTimeTableSignal.map { timeTableSignal_ =>

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
