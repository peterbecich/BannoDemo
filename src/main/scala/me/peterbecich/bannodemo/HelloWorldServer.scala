package me.peterbecich.bannodemo

import cats.effect._
import fs2.Stream
import io.circe._
import java.io.File
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io._
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.middleware._
import org.http4s.util.ExitCode
import org.http4s.util.StreamApp


import java.time.ZonedDateTime

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import me.peterbecich.bannodemo.twitter.TwitterAccumulators
import me.peterbecich.bannodemo.twitter.TwitterAverages
import me.peterbecich.bannodemo.twitter.TwitterStats
import me.peterbecich.bannodemo.twitter.TwitterStats._

object HelloWorldServer extends StreamApp[IO] with Http4sDsl[IO] {
  // http://http4s.org/v0.18/middleware/
  // http://http4s.org/v0.18/cors/

  val serverStart: ZonedDateTime = ZonedDateTime.now()

  val peter = "http://peterbecich.me"

  val originConfig = CORSConfig(
    anyOrigin = false,
    allowedOrigins = Set(peter),
    allowCredentials = false,
    maxAge = 1.day.toSeconds
  )

  // def service(stats: TwitterStats) =
  val service =
    HttpService[IO] {
      case GET -> Root / "hello" / name =>
        Ok(Json.obj("message" -> Json.fromString(s"Hello, ${name}")))
      case GET -> Root / "tweetCount" =>
        Ok(TwitterAccumulators.TweetCount.getCount.toString)
      case GET -> Root / "emojiTweetCount" =>
        Ok(TwitterAccumulators.EmojiTweetCount.getCount.toString)
      case GET -> Root / "urlTweetCount" =>
        Ok(TwitterAccumulators.URLTweetCount.getCount.toString)
      case GET -> Root / "picTweetCount" =>
        Ok(TwitterAccumulators.PicTweetCount.getCount.toString)
      case GET -> Root / "hashtagTweetCount" =>
        Ok(TwitterAccumulators.HashtagTweetCount.getCount.toString)
      // case GET -> Root / "stats" =>
      //   Ok(TwitterStats.getTwitterStatsJSON)
      // case GET -> Root / "averages" =>
      //   Ok(statsPayload.take(1))
      case GET -> Root / "bannoDemo" =>
        StaticFile.fromFile[IO](new File("/srv/static/index.html")).getOrElseF(NotFound())
      case GET -> Root / filename =>
        StaticFile.fromFile[IO](new File("/srv/static/"++filename)).getOrElseF(NotFound())
    }

  // def corsOriginService(stats: TwitterStats) =
  //   CORS(service(stats), originConfig)

  val corsOriginService =
    CORS(service, originConfig)
  
  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] =
    Stream.eval(TwitterStats.collectStats).flatMap { stats =>
      BlazeBuilder[IO]
        .bindHttp(8080, "0.0.0.0")
        .mountService(corsOriginService, "/")
        .serve
    }
  
  // def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] =
  //   BlazeBuilder[IO]
  //     .bindHttp(8080, "0.0.0.0")
  //     .mountService(corsOriginService, "/")
  //     .serve
  //     .concurrently(Stream.eval_(TwitterStats.collectStats))

}
