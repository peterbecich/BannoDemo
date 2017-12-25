package me.peterbecich.bannodemo

import cats.effect._
import io.circe._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io._
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.util.StreamApp
import org.http4s.util.ExitCode

import java.io.File

import fs2.Stream

import scala.concurrent.ExecutionContext.Implicits.global

import me.peterbecich.bannodemo.twitter.TwitterAccumulators

object HelloWorldServer extends StreamApp[IO] with Http4sDsl[IO] {
  val service = HttpService[IO] {
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
      // http://http4s.org/v0.18/static/
    case GET -> Root / "bannoDemo" =>
      StaticFile.fromFile[IO](new File("BannoDemo-frontend/dist/index.html")).getOrElseF(NotFound())

      
  }

  // https://oss.sonatype.org/service/local/repositories/releases/archive/co/fs2/fs2-core_2.12/0.9.6/fs2-core_2.12-0.9.6-javadoc.jar/!/fs2/Stream$.html#eval_[F[_],A](fa:F[A]):fs2.Stream[F,Nothing]
  // https://oss.sonatype.org/service/local/repositories/releases/archive/co/fs2/fs2-core_2.12/0.9.6/fs2-core_2.12-0.9.6-javadoc.jar/!/fs2/Stream.html#mergeDrainR[F2[_],O2](s2:fs2.Stream[F2,O2])(implicitS:fs2.util.Sub1[F,F2],implicitF2:fs2.util.Async[F2]):fs2.Stream[F2,O]
  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] =
    BlazeBuilder[IO]
      .bindHttp(8080, "0.0.0.0")
      .mountService(service, "/")
      .serve.concurrently((Stream.eval_(TwitterAccumulators.accumulateTwitter)))

    // Stream.eval_(TwitterAccumulators.accumulateTwitter).flatMap { (_: Nothing) =>
    //   BlazeBuilder[IO]
    //     .bindHttp(8080, "0.0.0.0")
    //     .mountService(service, "/")
    //     .serve
    // }
}
