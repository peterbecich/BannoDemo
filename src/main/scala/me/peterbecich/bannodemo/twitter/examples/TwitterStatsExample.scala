package me.peterbecich.bannodemo.twitter.examples

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

object TwitterStatsExample {
  import io.circe._

  
  // val pipeline: IO[Unit] = collectStats.flatMap { stats =>
  //   stats
  //     .take(1000)
  //     .map(_.toString)
  //     .through(fs2.text.utf8Encode)
  //     .observe(fs2.io.stdout)
  //     .drain
  //     .run
  // }

  

  // def main(args: Array[String]): Unit = {
  //   println("twitter stats example")

  //   pipeline.unsafeRunSync()

  // }

}

