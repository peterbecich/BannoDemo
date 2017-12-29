package me.peterbecich.bannodemo.twitter

import cats._
import cats.implicits._
import cats.syntax.all._
import cats.effect.{IO, Sync}

import fs2._

import com.danielasfregola.twitter4s.entities.Tweet

import scala.collection.concurrent.TrieMap

import java.time.{LocalDateTime, ZoneOffset}

import me.peterbecich.bannodemo.twitter.TwitterStats.getTweetTime

object TwitterAverages {
  val hour: Long = 60*60

  abstract class TwitterAverage {
    // Time Table is a hashmap of the prior 3600 seconds
    type TimeTable = TrieMap[LocalDateTime, Long]

    val emptyTimeTable: Stream[IO, TimeTable] =
      Stream.eval(IO(TrieMap.empty[LocalDateTime, Long]))

    def incrementTime(timetable: TimeTable)(timestamp: LocalDateTime): IO[TimeTable] = IO {
      val count: Long = timetable.getOrElse(timestamp, 0)
      timetable.put(timestamp, count+1) // TODO potential for miscount with concurrent access???
      timetable
    }

    // remove timestamps from table if they are beyond a certain age, in seconds
    def filterTimeThreshold(secondsThreshold: Long = hour)
      (timeTable: TimeTable): IO[TimeTable] = IO {
      // TODO get time zones right
      val zone = ZoneOffset.ofHours(0)
      val now = LocalDateTime.now()
      def diffUnderThreshold(ts1: LocalDateTime): Boolean = {
        (now.toEpochSecond(zone) - ts1.toEpochSecond(zone)) < secondsThreshold
      }

      timeTable.filter((kv) => diffUnderThreshold(kv._1))
    }

    val incrementTimePipe: Pipe2[IO, Tweet, TimeTable, TimeTable] =
      (tweetInput: Stream[IO, Tweet], timeTableInput: Stream[IO, TimeTable]) =>
    tweetInput.flatMap { tweet =>
      timeTableInput.flatMap { timeTable =>
        val timestamp = getTweetTime(tweet)
        Stream.eval(incrementTime(timeTable)(timestamp))
      }
    }

    val filterTimeThresholdPipe: Pipe[IO, TimeTable, TimeTable] =
      _.flatMap(timeTable => Stream.eval(filterTimeThreshold(hour)(timeTable)))

  }


}
