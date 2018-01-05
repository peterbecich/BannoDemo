package me.peterbecich.bannodemo.twitter.stats.examples

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
import me.peterbecich.bannodemo.twitter.stats.TwitterAverages
import me.peterbecich.bannodemo.twitter.TwitterSource

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


object TwitterAveragesExample {
  import fs2.{io, text}

  import TwitterAverages._

  val averageTwitter2: IO[Unit] =
    IO(println("acquire Twitter stream")).flatMap { _ =>
      TwitterSource.createTwitterStream.flatMap { twitterStream =>
        TwitterAverages.makeTwitterAverages.flatMap { case averagePipe =>
          IO(println("acquired Twitter stream and average pipe")).flatMap { _ =>
            twitterStream
              .through(averagePipe)
              .drain
              .run
          }
        }
      }
    }

  // def main(args: Array[String]): Unit = {
  //   println("twitter averages example")

  //   averageTwitter2.unsafeRunSync()
  // }
 
}
