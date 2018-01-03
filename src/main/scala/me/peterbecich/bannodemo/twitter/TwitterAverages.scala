package me.peterbecich.bannodemo.twitter

import cats._
import cats.implicits._
import cats.syntax.all._
import cats.effect.{IO, Sync}

import fs2.{Stream, Pipe, Scheduler}
import fs2.async.mutable.Signal
import fs2.async.immutable.{Signal => ISignal}

import com.danielasfregola.twitter4s.entities.Tweet

import scala.collection.concurrent.TrieMap

import java.time.{LocalDateTime, ZoneOffset, Duration}
import java.time.temporal.ChronoUnit

import me.peterbecich.bannodemo.twitter.TwitterStats.getTweetTime

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


object TwitterAverages {

  private val tweetAverage: IO[TwitterAverage] = TwitterAverage.makeAverage("TweetAverage", (_) => true)
  private val emojiAverage: IO[TwitterAverage] = TwitterAverage.makeAverage("EmojiAverage", (_) => true)


  object JSON {
    import io.circe._
    import io.circe.Encoder
    import io.circe.syntax._
    import io.circe.literal._
    import io.circe.generic.semiauto._

    import cats.instances.map._

    import scala.collection.immutable.HashMap

    import me.peterbecich.bannodemo.JSON.Common._
    import me.peterbecich.bannodemo.twitter.TwitterAverage.JSON._

    type AveragesPayload = Map[String, AveragePayload]

    // implicit val averagesPayloadEncoder: Encoder[AveragesPayload] = deriveEncoder

    def makeAveragesPayload(averages: List[AveragePayload]): AveragesPayload =
      averages.map { avePayload => (avePayload.name, avePayload) }.toMap

    def makeAveragesPayloadJson(averages: List[AveragePayload]): Json =
      makeAveragesPayload(averages).asJson

  }


  private val makeAverages: IO[List[TwitterAverage]] =
    Traverse[List].sequence(List(tweetAverage, emojiAverage))

  private def passThru[A]: Pipe[IO, A, A] = stream => stream

  private def pipeConcatenationMonoid[A] = new Monoid[Pipe[IO, A, A]] {
    def combine(pipe1: Pipe[IO, A, A], pipe2: Pipe[IO, A, A]): Pipe[IO, A, A] =
      pipe1.andThen(pipe2)
    def empty: Pipe[IO, A, A] = passThru[A]
  }

  def makeConcatenatedAveragePipe(averages: List[TwitterAverage]): IO[Pipe[IO, Tweet, Tweet]] =
    makeAverages.map { averages =>
      Foldable[List].foldMap(averages)(_.averagePipe)(pipeConcatenationMonoid)
    }

  def averagesPayloadStream(averages: List[TwitterAverage]): Stream[IO, io.circe.Json] =
    fs2.Scheduler[IO](2).flatMap { scheduler => 
      lazy val averagePayloads: List[Stream[IO, TwitterAverage.JSON.AveragePayload]] =
        averages.map(_.averagePayloadStream)

      lazy val streamListPayload: Stream[IO, List[TwitterAverage.JSON.AveragePayload]] =
        Traverse[List].sequence(averagePayloads)

      scheduler.delay(streamListPayload.map(JSON.makeAveragesPayloadJson), 20.second)

    }

  val makeTwitterAverages: IO[(Pipe[IO, Tweet, Tweet], Stream[IO, io.circe.Json])] =
    makeAverages.flatMap { averages =>
      makeConcatenatedAveragePipe(averages).map { pipe =>
        val averagesPayload = averagesPayloadStream(averages)
        (pipe, averagesPayload)
      }
    }

}

object TwitterAveragesExample {
  import fs2.{io, text}

  import TwitterAverages._

  val averageTwitter2: IO[Unit] =
    IO(println("acquire Twitter stream")).flatMap { _ =>
      TwitterQueue.createTwitterStream.flatMap { twitterStream =>
        TwitterAverages.makeTwitterAverages.flatMap { case (averagePipe, _) =>
          IO(println("acquired Twitter stream and average pipe")).flatMap { _ =>
            twitterStream
              .through(averagePipe)
              .drain
              .run
          }
        }
      }
    }

  def main(args: Array[String]): Unit = {
    println("twitter averages example")

    averageTwitter2.unsafeRunSync()
  }
 
}
