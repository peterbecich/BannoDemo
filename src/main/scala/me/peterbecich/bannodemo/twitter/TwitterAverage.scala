package me.peterbecich.bannodemo.twitter

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
    val average: Double = sum * 1.0 / seconds
    def add(s: Long, n: Long): CountAccumulator
    val ts: LocalDateTime
  }

  case class SecondCountAccumulator(
    sum: Long = 0,
    count: Long = 0,
    ts: LocalDateTime = LocalDateTime.now()
  ) extends CountAccumulator {
    val seconds = 1
    def add(s: Long, n: Long): SecondCountAccumulator = this.copy(sum+s, count+n)
  }
  case class MinuteCountAccumulator(
    sum: Long = 0,
    count: Long = 0,
    ts: LocalDateTime = LocalDateTime.now()
  ) extends CountAccumulator {
    val seconds = minute
    def add(s: Long, n: Long): MinuteCountAccumulator = this.copy(sum+s, count+n)
  }
  case class HourCountAccumulator(
    sum: Long = 0,
    count: Long = 0,
    ts: LocalDateTime = LocalDateTime.now()
  ) extends CountAccumulator {
    val seconds = hour
    def add(s: Long, n: Long): HourCountAccumulator = this.copy(sum+s, count+n)
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

    def makeTestPayload(
      name: String,
      secondCountAcc: SecondCountAccumulator
    ): AveragePayload =
      AveragePayload(name,
        secondCountAcc.average, secondCountAcc.ts,
        secondCountAcc.average, secondCountAcc.ts,
        secondCountAcc.average, secondCountAcc.ts
      )
    
    private def _makeAveragePayloadJson(averagePayload: AveragePayload): Json =
      averagePayload.asJson

    def makeAveragePayloadJson(
      name: String,
      secondCountAcc: SecondCountAccumulator,
      minuteCountAcc: MinuteCountAccumulator,
      hourCountAcc: HourCountAccumulator
    ): Json =
      _makeAveragePayloadJson(makeAveragePayload(name, secondCountAcc, minuteCountAcc, hourCountAcc))

    
  }

  private def makeTimeTableSignal: IO[TimeTableSignal] =
    Signal.apply(TrieMap.empty[LocalDateTime, Long])(IO.ioEffect, global)

  def makeAverage(_name: String, _predicate: Tweet => Boolean): IO[TwitterAverage] = for {
    _timeTableSignal <- makeTimeTableSignal
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

  // val averagePayloadStream: Stream[IO, JSON.AveragePayload] =
  //   Stream.repeatEval {
  //     IO(println("produce average payload")).flatMap(_ => for {
  //       secondCountAccumulator <- secondCountAccumulatorSignal.get
  //       minuteCountAccumulator <- minuteCountAccumulatorSignal.get
  //       hourCountAccumulator <- hourCountAccumulatorSignal.get
  //     } yield JSON.makeAveragePayload(name, secondCountAccumulator, minuteCountAccumulator, hourCountAccumulator))
  //   }

  // def averagePayloadStream: Stream[IO, JSON.AveragePayload] =
  //   secondCountAccumulatorSignal.discrete.map { secondCountAccumulator =>
  //     // minuteCountAccumulatorSignal.discrete.flatMap { minuteCountAccumulator =>
  //     //   hourCountAccumulatorSignal.discrete.map { hourCountAccumulator =>
  //         JSON.makeTestPayload(name, secondCountAccumulator)
  //     //   }
  //     // }
  //   }

  val averagePayloadStream: Stream[IO, JSON.AveragePayload] =
    Stream.eval {
      IO(println("produce average payload "+LocalDateTime.now().toString())).flatMap { _ =>
        secondCountAccumulatorSignal.refresh.flatMap { _ => 
          secondCountAccumulatorSignal.get.map { secondCountAccumulator =>
            println("second count acc: "+secondCountAccumulator)
            JSON.makeTestPayload(name, secondCountAccumulator)
          }
        }
      }
    }
  

    // for { 
    //   secondCountAccumulator <- secondCountAccumulatorSignal.discrete
    //   minuteCountAccumulator <- minuteCountAccumulatorSignal.discrete
    //   hourCountAccumulator <- hourCountAccumulatorSignal.discrete
    // } yield JSON.makeAveragePayload(name, secondCountAccumulator, minuteCountAccumulator, hourCountAccumulator)
  

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

  private lazy val priorSecondTimeTable: TimeTableSignal =
    timeTableSignal.imap { timeTable =>
      val zone = ZoneOffset.ofHours(0)
      val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
      timeTable.filter((kv) => kv._1 == now)
    }(identity)

  private lazy val priorMinuteTimeTable: TimeTableSignal =
    truncateTimeTable(60)

  private lazy val priorHourTimeTable: TimeTableSignal =
    truncateTimeTable(hour)

  // sum of tweets in the past second
  private lazy val secondSumSignal: ISignal[IO, (Long, Long)] =
    priorSecondTimeTable.map { timeTable =>
      (timeTable.values.sum, timeTable.size)
    }

  // sum of tweets in the past minute, from this second
  private lazy val minuteSumSignal: ISignal[IO, (Long, Long)] =
    priorMinuteTimeTable.map { timeTable =>
      (timeTable.values.sum, timeTable.size)
    }

  // sum of tweets in the past hour, from this second
  private lazy val hourSumSignal: ISignal[IO, (Long, Long)] =
    priorHourTimeTable.map { timeTable =>
      (timeTable.values.sum, timeTable.size)
    }


  // calculates second averages
  private lazy val calculateSecondAverage: Stream[IO, Unit] =
    secondSumSignal.discrete.flatMap { case (secondSum, secondCount) =>
      Stream.eval(secondCountAccumulatorSignal.modify { _.add(secondSum, secondCount) }).drain
    }
  
  // calculates minute averages
  private lazy val calculateMinuteAverage: Stream[IO, Unit] =
    minuteSumSignal.discrete.flatMap { case (minuteSum, minuteCount) =>
      Stream.eval(minuteCountAccumulatorSignal.modify { _.add(minuteSum, minuteCount) }).drain
    }

  // calculates hourly averages
  private lazy val calculateHourlyAverage: Stream[IO, Unit] =
    hourSumSignal.discrete.flatMap { case (hourSum, hourCount) =>
      Stream.eval(hourCountAccumulatorSignal.modify { countAcc =>
        val _countAcc = countAcc.add(hourSum, hourCount)
        // println((_countAcc.count * 1.0 / hour) + "   " + _countAcc.count + "  hourSum: " + hourSum + "  hourCount: " + hourCount)
        _countAcc
      }).drain
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
    // .concurrently(printRecentCount)
    .concurrently(calculateHourlyAverage)
    .concurrently(calculateMinuteAverage)
    .concurrently(calculateSecondAverage)

}
