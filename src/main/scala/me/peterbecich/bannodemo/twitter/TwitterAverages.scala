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

  val hour: Long = 60*60

  // Time Table is a hashmap of the prior 3600 seconds
  type TimeTable = TrieMap[LocalDateTime, Long]
  def createTimeTableSignal: IO[Signal[IO, TimeTable]] =
    Signal.apply(TrieMap.empty[LocalDateTime, Long])(IO.ioEffect, global)
  
  abstract class TwitterAverage {
    val name: String

    private def incrementTime(timeTableSignal: Signal[IO, TimeTable])
      (timestamp: LocalDateTime): IO[Unit] =
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

    // remove timestamps from table if they are beyond a certain age, in seconds
    private def filterTimeThreshold(secondsThreshold: Long = hour)
      (timeTableSignal: Signal[IO, TimeTable]): IO[Unit] =
      timeTableSignal.get.flatMap { timeTable => 
        IO {
          // TODO get time zones right
          val zone = ZoneOffset.ofHours(0)
          val now = LocalDateTime.now()
          def diffUnderThreshold(ts1: LocalDateTime): Boolean = {
            (now.toEpochSecond(zone) - ts1.toEpochSecond(zone)) < secondsThreshold
          }
          timeTable.filter((kv) => diffUnderThreshold(kv._1))
        }
      }

    private def incrementTimePipe(timeTableSignal: Signal[IO, TimeTable]):
        Pipe[IO, Tweet, Tweet] =
      (tweetInput: Stream[IO, Tweet]) =>
    tweetInput.flatMap { tweet =>
      val timestamp = getTweetTime(tweet)
      Stream.eval(incrementTime(timeTableSignal)(timestamp)).flatMap { _ =>
        Stream.emit(tweet)
      }
    }

    private def filterTimeThresholdPipe(timeTableSignal: Signal[IO, TimeTable]):
        Pipe[IO, Tweet, Tweet] =
      s => s
        .flatMap(timeTable => Stream.eval(filterTimeThreshold(hour)(timeTableSignal)))
        .flatMap(_ => s)

    // val timeTableSizePipe: Pipe[IO, TimeTable, Int] =
    //   _.map(_.size)

    def averagePipe(timeTableSignal: Signal[IO, TimeTable]): Pipe[IO, Tweet, Tweet] =
      incrementTimePipe(timeTableSignal).andThen(filterTimeThresholdPipe(timeTableSignal))

  }

  case object TweetAverage extends TwitterAverage {
    val name = "TweetAverage"
  }


}

object TwitterAverageExample {
  import fs2.{io, text}

  import TwitterAverages._

  // .observe1 { (tweet) => IO { println(tweet.user.map(_.name).getOrElse("no name")) }}

  // val averageTwitter: IO[Unit] = for {
  //   twitterStream <- TwitterQueue.createTwitterStream
  //   timeTableSignal <- TwitterAverages.createTimeTableSignal
  // } twitterStream

  val averageTwitter: IO[Unit] =
    TwitterQueue.createTwitterStream.flatMap { twitterStream =>
      TwitterAverages.createTimeTableSignal.flatMap { timeTableSignal => 
        twitterStream.through(TweetAverage.averagePipe(timeTableSignal))
          .map(_.toString)
          .intersperse("\n")
          .through(text.utf8Encode)
          .observe(io.stdout)
          .drain.run
      }
    }


  def main(args: Array[String]): Unit = {
    println("twitter averages example")

    averageTwitter.unsafeRunSync()
  }

}
