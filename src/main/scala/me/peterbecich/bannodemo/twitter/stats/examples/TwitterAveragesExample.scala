package me.peterbecich.bannodemo.twitter.stats.examples

import cats._
import cats.effect.{IO, Sync}
import cats.implicits._
import cats.syntax.all._
import com.danielasfregola.twitter4s.entities.Tweet
import fs2.async.immutable.{Signal => ISignal}
import fs2.async.mutable.Signal
import fs2.{Stream, Pipe, Scheduler}
import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, ZoneOffset, Duration}
import me.peterbecich.bannodemo.twitter.TwitterSource
import me.peterbecich.bannodemo.twitter.TwitterStats.getTweetTime
import me.peterbecich.bannodemo.twitter.stats.TwitterAverages
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


object TwitterAveragesExample {
  import fs2.{io, text}

  import TwitterAverages._

  private lazy val schedulerStream: Stream[IO, Scheduler] = Scheduler.apply[IO](8)

  val helloStream: Stream[IO, String] = Stream.repeatEval(IO("hello!"))

  val throttledHelloStream: Stream[IO, Unit] =
    schedulerStream.flatMap { scheduler =>
      helloStream.zip(scheduler.fixedRate(2.second)(IO.ioEffect, global)).map(_._1)
    }
      .intersperse("\n")
      .through(fs2.text.utf8Encode[IO])
      .through(fs2.io.stdout)
      .drain
  
  def watchPayloadStream(payloadStream: Stream[IO, TwitterAverages.JSON.AveragesPayload]):
      Stream[IO, Unit] =
    schedulerStream.flatMap { scheduler =>
      payloadStream.zip(scheduler.fixedRate(2.second)(IO.ioEffect, global)).map(_._1)
    }
      .map(_.toString)
      .intersperse("\n PAYLOAD \n")
      .through(fs2.text.utf8Encode)
      .through(fs2.io.stdout)
      .drain
  

  val averageTwitter: IO[Unit] = Monad[IO].flatten({
    for {
      _ <- IO(println("acquire Twitter stream"))
      twitterStream <- TwitterSource.createTwitterStream
      tup <- TwitterAverages.makeTwitterAverages
      (averagePipe, payloadStream) = tup
      _ <- IO(println("acquired Twitter stream and average pipe"))
    } yield twitterStream
      .through(averagePipe)
      .concurrently(watchPayloadStream(payloadStream))
      .concurrently(throttledHelloStream)
      .drain
      .run
    
  })

  def main(args: Array[String]): Unit = {
    println("twitter averages example")

    averageTwitter.unsafeRunSync()
  }
 
}

object FixedRateExample {

  private lazy val schedulerStream: Stream[IO, Scheduler] = Scheduler.apply[IO](8)

  val helloStream: Stream[IO, String] = Stream.repeatEval(IO("hello!"))
  val goodbyeStream: Stream[IO, String] = Stream.repeatEval(IO("goodbye!"))

  val throttledHelloStream: Stream[IO, Unit] =
    schedulerStream.flatMap { scheduler =>
      helloStream.zip(scheduler.fixedRate(2.second)(IO.ioEffect, global)).map(_._1)
    }
      .intersperse("\n")
      .through(fs2.text.utf8Encode[IO])
      .through(fs2.io.stdout)
      .drain

  val throttledGoodbyeStream: Stream[IO, Unit] =
    schedulerStream.flatMap { scheduler =>
      goodbyeStream.zip(scheduler.fixedRate(3.second)(IO.ioEffect, global)).map(_._1)
    }
      .intersperse("\n")
      .through(fs2.text.utf8Encode[IO])
      .through(fs2.io.stdout)
      .concurrently(throttledHelloStream)
      .drain

  def main(args: Array[String]): Unit =
    throttledGoodbyeStream.run.unsafeRunSync()




}
