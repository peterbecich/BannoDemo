package me.peterbecich.bannodemo.twitter

import cats._
import cats.implicits._
import cats.syntax.all._
import cats.effect.{IO, Sync}

import fs2._
import fs2.async.mutable.Signal

import com.danielasfregola.twitter4s.entities.Tweet

import scala.collection.concurrent.TrieMap

import java.time.{LocalDateTime, ZoneOffset}
import java.time.temporal.ChronoUnit

import me.peterbecich.bannodemo.twitter.TwitterStats.getTweetTime

import scala.concurrent.ExecutionContext.Implicits.global

object TwitterAverages {

  //val hour: Long = 60*60
  val hour: Long = 20

  // Time Table is a hashmap of the prior 3600 seconds
  type TimeTable = TrieMap[LocalDateTime, Long]

  // abstract class TwitterAverage {
  abstract class TwitterAverage {
    val name: String
    val timeTableSignal: Signal[IO, TimeTable]
    val predicate: Tweet => Boolean

    private def incrementTime(timestamp: LocalDateTime): IO[Unit] =
      timeTableSignal.get.flatMap { timeTable =>
        IO {
          println("incrementTime")
          val timestampTruncated: LocalDateTime =
            timestamp.truncatedTo(ChronoUnit.SECONDS)
          val count: Long = timeTable.getOrElse(timestampTruncated, 0)
          if(count > 0 && count % 15 == 0)
            println(name+" "+timestamp.toString()+" count: "+count+" time table size: "+timeTable.size)
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

    private def truncateTimeTable(secondsThreshold: Long)
      (timeTableSignal: Signal[IO, TimeTable]): IO[TimeTable] =
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

    private def priorHourTimeTable(timeTableSignal: Signal[IO, TimeTable]): IO[TimeTable] =
      truncateTimeTable(hour)(timeTableSignal)

    private def priorMinuteTimeTable(timeTableSignal: Signal[IO, TimeTable]): IO[TimeTable] =
       truncateTimeTable(60)(timeTableSignal)

    private def priorSecondTimeTable(timeTableSignal: Signal[IO, TimeTable]): IO[TimeTable] =
      timeTableSignal.get.map { timeTable =>
        val zone = ZoneOffset.ofHours(0)
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
        timeTable.filter((kv) => kv._1 == now)
      }

    // TODO should these be vals?
    def getHourSum: IO[Long] =
      priorHourTimeTable(timeTableSignal).map { timeTable =>
        timeTable.values.sum
      }

    def getMinuteSum: IO[Long] =
      priorMinuteTimeTable(timeTableSignal).map { timeTable =>
        timeTable.values.sum
      }

    def getSecondSum: IO[Long] =
      priorSecondTimeTable(timeTableSignal).map { timeTable =>
        timeTable.values.sum
      }
    
    val averagePipe: Pipe[IO, Tweet, Tweet] =
      incrementTimePipe.andThen(filterTimeThresholdPipe)

  }

  def createTimeTableSignal: IO[Signal[IO, TimeTable]] =
    Signal.apply(TrieMap.empty[LocalDateTime, Long])(IO.ioEffect, global)

  def printTimeTableSize(sig: Signal[IO, TimeTable]): Stream[IO, Unit] =
    sig.continuous.flatMap { timeTable => Stream.eval_(IO(println(timeTable.size))) }

  def makeAverage(_name: String, _predicate: Tweet => Boolean): IO[TwitterAverage] =
    createTimeTableSignal.map { timeTableSignal_ =>
      new TwitterAverage {
        val name = _name
        val timeTableSignal = timeTableSignal_
        val predicate = _predicate
      }
    }

  val tweetAverage = makeAverage("TweetAverage", (_) => true)
  val emojiAverage = makeAverage("EmojiAverage", (_) => true)

  val makeAverages: IO[List[TwitterAverage]] =
    Traverse[List].sequence(List(tweetAverage, emojiAverage))

  def passThru[A]: Pipe[IO, A, A] = stream => stream

  def pipeConcatenationMonoid[A] = new Monoid[Pipe[IO, A, A]] {
    def combine(pipe1: Pipe[IO, A, A], pipe2: Pipe[IO, A, A]): Pipe[IO, A, A] =
      pipe1.andThen(pipe2)
    def empty: Pipe[IO, A, A] = passThru[A]
  }

  val makeConcatenatedAveragePipe: IO[Pipe[IO, Tweet, Tweet]] =
    makeAverages.map { averages =>
      Foldable[List].foldMap(averages)(_.averagePipe)(pipeConcatenationMonoid)
    }
}

object TwitterAverageExample {
  import fs2.{io, text}

  import TwitterAverages._

  val averageTwitter: IO[Unit] = for {
    _ <- IO(println("begin averaging streaming"))
    twitterStream <- TwitterQueue.createTwitterStream
    averagePipe <- TwitterAverages.makeConcatenatedAveragePipe
    _ <- IO(println("average pipe initialized"))
  } yield {
    twitterStream
      .through(averagePipe)
      .drain.run
  }

  def main(args: Array[String]): Unit = {
    println("twitter averages example")

    averageTwitter.unsafeRunSync()
  }
 
}
