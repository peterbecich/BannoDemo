package me.peterbecich.bannodemo.twitter

import cats.Applicative
import cats.effect._
import io.circe._
import io.circe.Encoder
import io.circe.syntax._
import io.circe.literal._
import io.circe.generic.semiauto._
import org.http4s.circe._
import me.peterbecich.bannodemo.JSON.Common._

import fs2.Stream
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import java.time.{LocalDateTime, ZonedDateTime}

import com.danielasfregola.twitter4s.entities.Tweet

import me.peterbecich.bannodemo.twitter.stats.TwitterAccumulators
import me.peterbecich.bannodemo.twitter.stats.TwitterAccumulators._
import me.peterbecich.bannodemo.twitter.stats.TwitterAverages

import me.peterbecich.bannodemo.HelloWorldServer.serverStart

/*
 https://docs.oracle.com/javase/8/docs/api/java/time/ZonedDateTime.html

 http://http4s.org/v0.18/json/
 http://http4s.org/v0.18/entity/
 */

// case class TwitterStats(
//   serverStartTimestamp: ZonedDateTime = serverStart,
//   statsTimestamp: ZonedDateTime = ZonedDateTime.now(),
//   tweetCount: Long,
//   emojiTweetCount: Long,
//   emojiPercentage: Double,
//   urlTweetCount: Long,
//   urlPercentage: Double,
//   picTweetCount: Long,
//   picPercentage: Double,
//   hashtagTweetCount: Long,
//   hashtagPercentage: Double
// )

object TwitterStats {

  import cats._
  import cats.implicits._

  object JSON {
    import io.circe._
    import io.circe.Encoder
    import io.circe.syntax._
    import io.circe.literal._
    import io.circe.generic.semiauto._

    import stats.TwitterAverages.JSON._
    import stats.TwitterAccumulators.JSON._

    case class StatsPayload (
      serverStartTimestamp: ZonedDateTime,
      statsTimestamp: ZonedDateTime,
      accumulators: AccumulatorsPayload,
      averages: AveragesPayload
    )

    implicit val statsPayloadEncoder: Encoder[StatsPayload] = deriveEncoder

  }

  val collectStats: IO[Unit] = 
    IO(println("acquire Twitter stream")).flatMap { _ =>
      TwitterSource.createTwitterStream.flatMap { twitterStream =>
        TwitterAccumulators.makeAccumulator.flatMap { countPipe =>
          TwitterAverages.makeTwitterAverages.flatMap { averagePipe =>
            twitterStream
              .through(countPipe)
              .through(averagePipe)
              .drain
              .run
              // .runAsync { case _ => IO(()) }.flatMap { _ =>
              //   IO(averagesPayloadStream)
              // }
          }
        }
      }
    }



  // http://www.java67.com/2016/03/how-to-convert-date-to-localdatetime-in-java8-example.html
  // TODO don't use server's time zone for all tweet timestamps
  implicit def dateToLocalDateTime(date: java.util.Date): LocalDateTime =
    LocalDateTime.ofInstant(date.toInstant(), serverStart.getZone())

  def getTweetTime(tweet: Tweet): LocalDateTime = dateToLocalDateTime(tweet.created_at)


}
 
