package me.peterbecich.bannodemo.twitter

import cats._
import cats.implicits._
import cats.syntax.all._
import cats.effect.{IO, Sync}

import fs2._

import com.danielasfregola.twitter4s.entities.Tweet

import scala.collection.concurrent.TrieMap

import java.time.{LocalDateTime, ZoneOffset}
import java.time.temporal.ChronoUnit

import me.peterbecich.bannodemo.twitter.TwitterStats.getTweetTime

object TwitterAverages {

  val hour: Long = 60*60

  abstract class TwitterAverage {
    val name: String
    
    // Time Table is a hashmap of the prior 3600 seconds
    type TimeTable = TrieMap[LocalDateTime, Long]

    private val emptyTimeTable: Stream[IO, TimeTable] =
      Stream.eval(IO(TrieMap.empty[LocalDateTime, Long]))

    private def incrementTime(timetable: TimeTable)
      (timestamp: LocalDateTime): IO[TimeTable] = IO {
      val timestampTruncated: LocalDateTime = timestamp.truncatedTo(ChronoUnit.SECONDS)
      val count: Long = timetable.getOrElse(timestampTruncated, 0)
      timetable.put(timestampTruncated, count+1)
      // TODO potential for miscount with concurrent access???
      timetable
    }

    // remove timestamps from table if they are beyond a certain age, in seconds
    private def filterTimeThreshold(secondsThreshold: Long = hour)
      (timeTable: TimeTable): IO[TimeTable] = IO {
      // TODO get time zones right
      val zone = ZoneOffset.ofHours(0)
      val now = LocalDateTime.now()
      def diffUnderThreshold(ts1: LocalDateTime): Boolean = {
        (now.toEpochSecond(zone) - ts1.toEpochSecond(zone)) < secondsThreshold
      }

      timeTable.filter((kv) => diffUnderThreshold(kv._1))
    }

    private val incrementTimePipe: Pipe2[IO, Tweet, TimeTable, TimeTable] =
      (tweetInput: Stream[IO, Tweet], timeTableInput: Stream[IO, TimeTable]) =>
    tweetInput.flatMap { tweet =>
      timeTableInput.flatMap { timeTable =>
        val timestamp = getTweetTime(tweet)
        // Stream.eval(IO(println(timestamp)))
        Stream.eval(incrementTime(timeTable)(timestamp))
      }
    }

    private val filterTimeThresholdPipe: Pipe[IO, TimeTable, TimeTable] =
      _.flatMap(timeTable => Stream.eval(filterTimeThreshold(hour)(timeTable)))

    val timeTableSizePipe: Pipe[IO, TimeTable, Int] =
      _.map(_.size)

    val averagePipe: Pipe[IO, Tweet, TimeTable] =
      (tweetInput: Stream[IO, Tweet]) =>
    incrementTimePipe(tweetInput, emptyTimeTable).through(filterTimeThresholdPipe)

  }

  case object TweetAverage extends TwitterAverage {
    val name = "TweetAverage"
  }


}

object TwitterAverageExample {
  import fs2.{io, text}
  import scala.concurrent.ExecutionContext.Implicits.global

  import TwitterAverages._

  val averageTwitter: IO[Unit] = TwitterQueue.createTwitterStream.flatMap { _
    // .observe1 { (tweet) => IO { println(tweet.user.map(_.name).getOrElse("no name")) }}
    .through(TweetAverage.averagePipe)
    .through(TweetAverage.timeTableSizePipe)
    .map(_.toString)
    .intersperse("\n")
    .through(text.utf8Encode)
    .observe(io.stdout)
    .drain.run
  }

  def main(args: Array[String]): Unit = {
    println("twitter averages example")

    averageTwitter.unsafeRunSync()
  }

}
