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


object TwitterAverage {

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
    def average: Double = sum * 1.0 / seconds
    def add(s: Long, n: Long): CountAccumulator
    val ts: LocalDateTime
  }

  case class SecondCountAccumulator(
    sum: Long = 0,
    count: Long = 0,
    ts: LocalDateTime
  ) extends CountAccumulator {
    val seconds = 1
    def add(s: Long, n: Long): SecondCountAccumulator =
      this.copy(sum+s, count+n, ts = LocalDateTime.now())
  }
  case class MinuteCountAccumulator(
    sum: Long = 0,
    count: Long = 0,
    ts: LocalDateTime
  ) extends CountAccumulator {
    val seconds = minute
    def add(s: Long, n: Long): MinuteCountAccumulator =
      this.copy(sum+s, count+n, ts = LocalDateTime.now())
  }
  case class HourCountAccumulator(
    sum: Long = 0,
    count: Long = 0,
    ts: LocalDateTime
  ) extends CountAccumulator {
    val seconds = hour
    def add(s: Long, n: Long): HourCountAccumulator =
      this.copy(sum+s, count+n, ts = LocalDateTime.now())
  }

  object JSON {
    import io.circe._
    import io.circe.Encoder
    import io.circe.syntax._
    import io.circe.literal._
    import io.circe.generic.semiauto._

    import me.peterbecich.bannodemo.JSON.Common._

    implicit val averagePayloadEncoder: Encoder[AveragePayload] = deriveEncoder

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

    // def makeTestPayload(
    //   name: String,
    //   secondCountAcc: SecondCountAccumulator
    // ): AveragePayload =
    //   AveragePayload(name,
    //     secondCountAcc.average, secondCountAcc.ts,
    //     secondCountAcc.average, secondCountAcc.ts,
    //     secondCountAcc.average, secondCountAcc.ts
    //   )
    
    // private def _makeAveragePayloadJson(averagePayload: AveragePayload): Json =
    //   averagePayload.asJson

    // def makeAveragePayloadJson(
    //   name: String,
    //   secondCountAcc: SecondCountAccumulator,
    //   minuteCountAcc: MinuteCountAccumulator,
    //   hourCountAcc: HourCountAccumulator
    // ): Json =
    //   _makeAveragePayloadJson(makeAveragePayload(name, secondCountAcc, minuteCountAcc, hourCountAcc))

    
  }

  private def makeTimeTableSignal: IO[TimeTableSignal] =
    Signal.apply(TrieMap.empty[LocalDateTime, Long])(IO.ioEffect, global)

  def makeAverage(_name: String, _predicate: Tweet => Boolean): IO[TwitterAverage] = for {
    _timeTableSignal <- makeTimeTableSignal
    _secondCountAccumulatorSignal <- Signal.apply {
      SecondCountAccumulator(ts = LocalDateTime.now())}(IO.ioEffect, global)
    _minuteCountAccumulatorSignal <- Signal.apply {
      MinuteCountAccumulator(ts = LocalDateTime.now())}(IO.ioEffect, global)
    _hourCountAccumulatorSignal <- Signal.apply {
      HourCountAccumulator(ts = LocalDateTime.now())}(IO.ioEffect, global)
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

  def _makeAverage(_name: String, _predicate: Tweet => Boolean):
      IO[(TwitterAverage, TimeTableSignal)] = for {
    _timeTableSignal <- makeTimeTableSignal
    _secondCountAccumulatorSignal <- Signal.apply {
      SecondCountAccumulator(ts = LocalDateTime.now())}(IO.ioEffect, global)
    _minuteCountAccumulatorSignal <- Signal.apply {
      MinuteCountAccumulator(ts = LocalDateTime.now())}(IO.ioEffect, global)
    _hourCountAccumulatorSignal <- Signal.apply {
      HourCountAccumulator(ts = LocalDateTime.now())}(IO.ioEffect, global)
  } yield {
    (new TwitterAverage {
      val name = _name
      val timeTableSignal = _timeTableSignal
      val predicate = _predicate
      val secondCountAccumulatorSignal = _secondCountAccumulatorSignal
      val minuteCountAccumulatorSignal = _minuteCountAccumulatorSignal
      val hourCountAccumulatorSignal = _hourCountAccumulatorSignal
     }, _timeTableSignal)
  }
  
  
}

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
  //   lazy val secondStream: Stream[IO, SecondCountAccumulator] =
  //     Stream.repeatEval(secondCountAccumulatorSignal.get).flatMap { acc =>
  //       Stream.eval(IO(println("payload second: "+acc))).flatMap { _ =>
  //         Stream.emit(acc)
  //       }
  //     }
  //   lazy val minuteStream: Stream[IO, MinuteCountAccumulator] =
  //     Stream.repeatEval(minuteCountAccumulatorSignal.get).flatMap { acc =>
  //       Stream.eval(IO(println("payload minute: "+acc))).flatMap { _ =>
  //         Stream.emit(acc)
  //       }
  //     }
  //   lazy val hourStream: Stream[IO, HourCountAccumulator] =
  //     Stream.repeatEval(hourCountAccumulatorSignal.get).flatMap { acc =>
  //       Stream.eval(IO(println("payload hour: "+acc))).flatMap { _ =>
  //         Stream.emit(acc)
  //       }
  //     }


    // def secondStream: Stream[IO, SecondCountAccumulator] =
    //   Stream.eval(secondCountAccumulatorSignal.get).map(acc => {println(acc); acc})
    // def minuteStream: Stream[IO, MinuteCountAccumulator] =
    //   Stream.eval(minuteCountAccumulatorSignal.get).map(acc => {println(acc); acc})
    // def hourStream: Stream[IO, HourCountAccumulator] =
    //   Stream.eval(hourCountAccumulatorSignal.get).map(acc => {println(acc); acc})


    lazy val secondStream: Stream[IO, SecondCountAccumulator] =
      secondCountAccumulatorSignal.continuous.flatMap { acc =>
        Stream.eval(IO(println("payload second: "+acc))).flatMap { _ =>
          Stream.emit(acc)
        }
      }
    lazy val minuteStream: Stream[IO, MinuteCountAccumulator] =
      minuteCountAccumulatorSignal.continuous.flatMap { acc =>
        Stream.eval(IO(println("payload minute: "+acc))).flatMap { _ =>
          Stream.emit(acc)
        }
      }
    lazy val hourStream: Stream[IO, HourCountAccumulator] =
      hourCountAccumulatorSignal.continuous.flatMap { acc =>
        Stream.eval(IO(println("payload hour: "+acc))).flatMap { _ =>
          Stream.emit(acc)
        }
      }
    


    lazy val zippedStreams: Stream[IO, ((SecondCountAccumulator, MinuteCountAccumulator), HourCountAccumulator)] =
      secondStream.zip(minuteStream).zip(hourStream)

    lazy val _zippedStreams: Stream[IO, (SecondCountAccumulator, MinuteCountAccumulator, HourCountAccumulator)] =
      zippedStreams.map { case ((second, minute), hour) => (second, minute, hour) }

    // lazy val payloadStream: Stream[IO, JSON.AveragePayload] = for {
    //   tup <- _zippedStreams
    //   (second, minute, hour) = tup
    //   _ <- Stream.eval(IO(println("make average payload "+LocalDateTime.now())))
    //   _ <- Stream.eval(IO(println("make average payload sec "+second)))
    //   _ <- Stream.eval(IO(println("make average payload min "+minute)))
    //   _ <- Stream.eval(IO(println("make average payload hour "+hour)))
    // } yield JSON.makeAveragePayload(name, second, minute, hour)

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

  private def truncateTimeTable(secondsThreshold: Long): IO[TimeTable] =
    timeTableSignal.get.map { timeTable =>
      val zone = ZoneOffset.ofHours(0)
      val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
      def diffUnderThreshold(ts1: LocalDateTime): Boolean = {
        val diff = now.toEpochSecond(zone) - ts1.toEpochSecond(zone)
        (diff) < secondsThreshold
      }
      timeTable.filter((kv) => diffUnderThreshold(kv._1))
    }

  private def priorSecondTimeTable: IO[TimeTable] =
    timeTableSignal.get.map { timeTable =>
      val zone = ZoneOffset.ofHours(0)
      val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
      timeTable.filter((kv) => kv._1 == now)
    }

  private def priorMinuteTimeTable: IO[TimeTable] =
    truncateTimeTable(60)

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
      scheduler.fixedRate(4.second)(IO.ioEffect, global).flatMap { _ =>
        Stream.eval(secondSum).flatMap { case (sum, count) =>
          Stream.eval {
            secondCountAccumulatorSignal.modify { acc =>
              acc.add(sum, count)
            }
          }.flatMap { _ =>
            Stream.eval(secondCountAccumulatorSignal.get.flatMap { acc => IO(println("second acc: "+acc)) })
          }
        }
      }
    }.drain
  
  // calculates minute averages
  private lazy val calculateMinuteAverage: Stream[IO, Unit] =
    schedulerStream.flatMap { scheduler =>
      scheduler.fixedRate(4.second)(IO.ioEffect, global).flatMap { _ =>
        Stream.eval(minuteSum).flatMap { case (sum, count) =>
          Stream.eval {
            minuteCountAccumulatorSignal.modify { acc =>
              acc.add(sum, count)
            }
          }.flatMap { _ =>
            Stream.eval(minuteCountAccumulatorSignal.get.flatMap { acc => IO(println("minute acc: "+acc)) })
          }
        }
      }
    }.drain

  // calculates hourly averages
  private lazy val calculateHourlyAverage: Stream[IO, Unit] =
    schedulerStream.flatMap { scheduler =>
      scheduler.fixedRate(4.second)(IO.ioEffect, global).flatMap { _ =>
        Stream.eval(hourSum).flatMap { case (sum, count) =>
          Stream.eval {
            hourCountAccumulatorSignal.modify { acc =>
              acc.add(sum, count)
            }
          }.flatMap { _ =>
            Stream.eval(hourCountAccumulatorSignal.get.flatMap { acc => IO(println("hour acc: "+acc)) })
          }
        }
      }
    }.drain


  private lazy val schedulerStream: Stream[IO, Scheduler] = Scheduler.apply[IO](8)

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
  private lazy val printRecentCount: Stream[IO, Unit] = recentCount
    .map { case (ts, count, timeTableSize) =>
      name + " " + ts.toString() + " count: " + count + " time table size: " + timeTableSize + "\n"}
    .intersperse("\n")
    .through(fs2.text.utf8Encode)
    .observe(fs2.io.stdout)
    .drain

  lazy val watchSecondSignal: Stream[IO, Unit] =
    schedulerStream.flatMap { scheduler =>
      scheduler.fixedRate(8.second)(IO.ioEffect, global).flatMap { _ =>
        Stream.eval(secondCountAccumulatorSignal.get)
      }
    }.map(acc => "second signal: "+acc.toString)
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

}
