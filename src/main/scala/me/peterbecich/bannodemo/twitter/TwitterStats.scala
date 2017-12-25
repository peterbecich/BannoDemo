package me.peterbecich.bannodemo.twitter

import io.circe._
import io.circe.Encoder
import io.circe.syntax._
import io.circe.literal._
import io.circe.generic.auto._
import org.http4s.circe._

import java.time.ZonedDateTime

import TwitterAccumulators._


/*
 https://docs.oracle.com/javase/8/docs/api/java/time/ZonedDateTime.html

 http://http4s.org/v0.18/json/
 http://http4s.org/v0.18/entity/
 */

case class TwitterStats(
  serverStartTimestamp: ZonedDateTime,
  statsTimestamp: ZonedDateTime,
  tweetCount: Long,
  emojiTweetCount: Long,
  urlTweetCount: Long,
  picTweetCount: Long,
  hashtagTweetCount: Long
)

object TwitterStats {
  // circular dependency?
  import me.peterbecich.bannodemo.HelloWorldServer.serverStart

  implicit val statsEncoder: Encoder[TwitterStats] =
    Encoder.instance { stats: TwitterStats =>
      json"""{"serverStartTimestamp": ${stats.serverStartTimestamp.toString}, "statsTimestamp": ${stats.statsTimestamp.toString}, "tweetCount": ${stats.tweetCount}, "emojiTweetCount": ${stats.emojiTweetCount}, "urlTweetCount": ${stats.urlTweetCount}, "picTweetCount": ${stats.picTweetCount}, "hashtagTweetCount": ${stats.hashtagTweetCount}}"""
    }

  def getTwitterStats: TwitterStats =
    TwitterStats(
      serverStart,
      ZonedDateTime.now(),
      TweetCount.getCount,
      EmojiTweetCount.getCount,
      URLTweetCount.getCount,
      PicTweetCount.getCount,
      HashtagTweetCount.getCount
    )

  def getTwitterStatsJSON: io.circe.Json =
    getTwitterStats.asJson

  
}
