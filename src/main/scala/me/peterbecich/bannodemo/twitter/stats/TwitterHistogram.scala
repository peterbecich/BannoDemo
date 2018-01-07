package me.peterbecich.bannodemo.twitter.stats

import cats._
import cats.implicits._
import cats.syntax.all._
import cats.effect.{IO, Sync}

import fs2.{Stream, Pipe, Scheduler}
import fs2.async.mutable.Signal
import fs2.async.immutable.{Signal => ISignal}

import com.danielasfregola.twitter4s.entities.Tweet

import scala.collection.concurrent.TrieMap
import scala.collection.immutable.HashSet

import java.time.{LocalDateTime, ZoneOffset, Duration}
import java.time.temporal.ChronoUnit

import me.peterbecich.bannodemo.twitter.TwitterStats.getTweetTime

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


object TwitterHistogram {

  type Histogram[K] = TrieMap[K, Long]
  type HistogramSignal[K] = Signal[IO, Histogram[K]]

  object JSON {
    import io.circe._
    import io.circe.Encoder
    import io.circe.syntax._
    import io.circe.literal._
    import io.circe.generic.semiauto._

    import me.peterbecich.bannodemo.JSON.Common._

    // implicit val averagePayloadEncoder: Encoder[AveragePayload] = deriveEncoder

    case class HistogramPayload[K](
      name: String
    )

    def makeHistogramPayload[K](
      name: String
    ): HistogramPayload[K] =
      HistogramPayload[K](name)
  }

  private def makeHistogramSignal[K]: IO[HistogramSignal[K]] =
    Signal.apply(TrieMap.empty[K, Long])(IO.ioEffect, global)

  def makeTwitterHistogram[K](_name: String, _predicate: Tweet => K, _bins: Set[K]):
      IO[TwitterHistogram[K]] = for { 
    _histogramSignal <- makeHistogramSignal[K]
  } yield {
    new TwitterHistogram[K] {
      val name = _name
      val histogramSignal = _histogramSignal
      val predicate = _predicate
      val bins = (new HashSet()) ++ _bins
     }
  }
  
}

abstract class TwitterHistogram[K] {

  import TwitterHistogram._

  val name: String
  val histogramSignal: HistogramSignal[K]
  val predicate: Tweet => K

  val bins: HashSet[K]

  private def incrementKey(tweet: Tweet): IO[Unit] = {
    val key: K = predicate(tweet)
    if(bins.contains(key)) {
      histogramSignal.modify { histogram =>
        val count: Long = histogram.getOrElse(key, 0)
        val _histogram: Histogram[K] = histogram += ((key, count+1))
        _histogram
      }.map(_ => ())
    } else IO (())
  }

  private val incrementKeyPipe: Pipe[IO, Tweet, Tweet] =
    (tweetInput: Stream[IO, Tweet]) =>
    tweetInput.flatMap { tweet =>
      Stream.eval(incrementKey(tweet)).flatMap { _ =>
        Stream.emit(tweet)
      }
    }

  val histogramPipe: Pipe[IO, Tweet, Tweet] = incrementKeyPipe

}
