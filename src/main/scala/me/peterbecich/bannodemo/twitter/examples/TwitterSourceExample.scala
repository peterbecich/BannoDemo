package me.peterbecich.bannodemo.twitter.examples

import cats._
import cats.effect.{IO, Sync}
import cats.syntax.all._
import com.danielasfregola.twitter4s.TwitterStreamingClient
import com.danielasfregola.twitter4s.entities.Tweet
import com.danielasfregola.twitter4s.entities.streaming.{StreamingMessage, CommonStreamingMessage, UserStreamingMessage, SiteStreamingMessage}
import fs2._
import fs2.async.mutable.Queue
import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import scala.concurrent.ExecutionContext.Implicits.global

import me.peterbecich.bannodemo.twitter.TwitterSource

object TwitterSourceExample {

  import fs2._

  // val printTwitter = for (
  //   twitterStream <- TwitterQueue.createTwitterStream
  //   val drained = twitterStream.map(_.text).take(64).through(text.utf8Encode).observe(io.stdout).drain.run
  //   // val drained = twitterStream.map(_.text).through(text.utf8Encode).observe(io.stdout).drain
  // ) yield drained  // TODO do this without yield

  val printTwitter: IO[Unit] = TwitterSource.createTwitterStream.flatMap { twitterStream =>
    twitterStream
      .map(tweet => tweet.user.map(_.name).getOrElse("nobody"))
      .intersperse("\n")
      // .through(text.lines)
      .through(text.utf8Encode)
      .observe(io.stdout)
      .drain.run
  }

  // def main(args: Array[String]): Unit = {
  //   println("twitter queue example, with FS2")

  //   printTwitter.unsafeRunSync()
  // }


}
