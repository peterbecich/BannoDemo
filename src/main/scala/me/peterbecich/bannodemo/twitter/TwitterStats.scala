package me.peterbecich.bannodemo.twitter

import cats.Applicative
import cats.effect._
import io.circe._
import io.circe.Encoder
import io.circe.syntax._
import io.circe.literal._
import io.circe.generic.semiauto._
import org.http4s.circe._

import fs2._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import java.time.{LocalDateTime, ZonedDateTime}

import com.danielasfregola.twitter4s.entities.Tweet

import TwitterAccumulators._

import me.peterbecich.bannodemo.HelloWorldServer.serverStart

/*
 https://docs.oracle.com/javase/8/docs/api/java/time/ZonedDateTime.html

 http://http4s.org/v0.18/json/
 http://http4s.org/v0.18/entity/
 */

case class TwitterStats(
  serverStartTimestamp: ZonedDateTime = serverStart,
  statsTimestamp: ZonedDateTime = ZonedDateTime.now(),
  tweetCount: Long,
  emojiTweetCount: Long,
  emojiPercentage: Double,
  urlTweetCount: Long,
  urlPercentage: Double,
  picTweetCount: Long,
  picPercentage: Double,
  hashtagTweetCount: Long,
  hashtagPercentage: Double
)

object TwitterStats {

  // http://www.java67.com/2016/03/how-to-convert-date-to-localdatetime-in-java8-example.html
  // TODO don't use server's time zone for all tweet timestamps
  implicit def dateToLocalDateTime(date: java.util.Date): LocalDateTime =
    LocalDateTime.ofInstant(date.toInstant(), serverStart.getZone())

  def getTweetTime(tweet: Tweet): LocalDateTime = dateToLocalDateTime(tweet.created_at)

  implicit val ZonedDateTimeEncoder: Encoder[ZonedDateTime] =
    Encoder.instance { zonedDateTime => json"""${zonedDateTime.toString}""" }

  /*
   http://circe.github.io/circe/api/io/circe/numbers/BiggerDecimal.html
   */
  implicit val statsEncoder: Encoder[TwitterStats] = deriveEncoder

  private def makeTwitterStats(
    tweetCount: Long,
    emojiTweetCount: Long,
    emojiPercentage: Double,
    urlTweetCount: Long,
    urlPercentage: Double,
    picTweetCount: Long,
    picPercentage: Double,
    hashtagTweetCount: Long,
    hashtagPercentage: Double): TwitterStats =
    TwitterStats(serverStart, ZonedDateTime.now(), tweetCount, emojiTweetCount, emojiPercentage, urlTweetCount, urlPercentage, picTweetCount, picPercentage, hashtagTweetCount, hashtagPercentage)

  // https://typelevel.org/cats/api/cats/Applicative.html#ap9[A0,A1,A2,A3,A4,A5,A6,A7,A8,Z](f:F[(A0,A1,A2,A3,A4,A5,A6,A7,A8)=%3EZ])(f0:F[A0],f1:F[A1],f2:F[A2],f3:F[A3],f4:F[A4],f5:F[A5],f6:F[A6],f7:F[A7],f8:F[A8]):F[Z]
  def getTwitterStats: IO[TwitterStats] =
    Applicative[IO].ap9(IO(makeTwitterStats _))(
      TweetCount.getCount,
      EmojiTweetCount.getCount,
      EmojiTweetCount.getPercentage,
      URLTweetCount.getCount,
      URLTweetCount.getPercentage,
      PicTweetCount.getCount,
      PicTweetCount.getPercentage,
      HashtagTweetCount.getCount,
      HashtagTweetCount.getPercentage
    )

  def getTwitterStatsJSON: IO[Json] =
    getTwitterStats.map(_.asJson)

}
