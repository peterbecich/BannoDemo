package me.peterbecich.bannodemo.twitter.stats

import cats._
import cats.implicits._
import cats.syntax.all._
import cats.effect.{IO, Sync}

import fs2.{Stream, Pipe, Scheduler}
import fs2.async.mutable.Signal
import fs2.async.immutable.{Signal => ISignal}

import com.danielasfregola.twitter4s.entities.Tweet

import scala.collection.immutable.IndexedSeq
import scala.collection.concurrent.TrieMap

import java.time.{LocalDateTime, ZoneOffset, Duration}
import java.time.temporal.ChronoUnit


import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object TwitterHistograms {

  // private lazy val urlHistogram: IO[TwitterHistogram] =
  //   TwitterHistogram.makeTwitterHistogramRegex("URL", raw"http.*?[com|org]".r)

  private lazy val urlHistogram: IO[TwitterHistogram] =
    TwitterHistogram.makeTwitterHistogramRegex("URL", raw"http\S+\.(com|org|net|co|c)?".r)

  private lazy val urlEndpointHistogram: IO[TwitterHistogram] =
    TwitterHistogram.makeTwitterHistogramRegex("URL Endpoint", raw"http\S+\.(com|org|net|co|c)/\S+".r)
  
  private lazy val hashtagHistogram: IO[TwitterHistogram] =
    TwitterHistogram.makeTwitterHistogramRegex("Hashtag", raw"""\#\S+""".r)

  import me.peterbecich.bannodemo.emojis.Emojis._

  // private lazy val emojisHistogram: IO[TwitterHistogram] =
  //   emojis match {
  //     case Left(error) => {
  //       println("error retrieving emojis from disk")
  //       TwitterHistogram.makeTwitterHistogram("Emojis", (_: Tweet) => Seq(), _bins = IndexedSeq.empty[String], _growBins = false)
  //     }
  //     case Right(emojis) => {
  //       println("some decoded emojis:")
  //       emojis.take(16).map(_.unified).foreach(println(_))
        
  //       val bins = emojis.map(emoji => "q").toIndexedSeq
  //       //         "unified": "1F1E8-1F1F4",
  //       // val regex = raw"""\w\w\w\w\w-\w\w\w\w\w""".r


  //       def keys(tweet: Tweet) = bins.filter(bin => tweet.text.contains(bin))
        
  //       TwitterHistogram.makeTwitterHistogram("Emojis", keys, _bins = bins, _growBins = false)

  //     }


  // }


  private lazy val makeHistograms: IO[List[TwitterHistogram]]=
    Traverse[List].sequence(List(urlHistogram, urlEndpointHistogram, hashtagHistogram))




  object JSON {
    import io.circe._
    import io.circe.Encoder
    import io.circe.syntax._
    import io.circe.literal._
    import io.circe.generic.semiauto._

    import cats.instances.map._

    import scala.collection.immutable.HashMap

    type HistogramsPayload = Map[String, TwitterHistogram.JSON.HistogramPayload]

    def makeHistogramsPayload(histograms: List[TwitterHistogram.JSON.HistogramPayload]): HistogramsPayload =
      histograms.map { histogram =>
        (histogram.name, histogram)
      }.toMap

  }

  private def passThru[A]: Pipe[IO, A, A] = stream => stream

  private def pipeConcatenationMonoid[A] = new Monoid[Pipe[IO, A, A]] {
    def combine(pipe1: Pipe[IO, A, A], pipe2: Pipe[IO, A, A]): Pipe[IO, A, A] =
      pipe1.andThen(pipe2)
    def empty: Pipe[IO, A, A] = passThru[A]
  }

  def makeConcatenatedHistogramPipe(histograms: List[TwitterHistogram]): IO[Pipe[IO, Tweet, Tweet]] =
    IO(Foldable[List].foldMap(histograms)(_.histogramPipe)(pipeConcatenationMonoid))

  private def sequenceStreams(listStream: List[Stream[IO,TwitterHistogram.JSON.HistogramPayload]]):
      Stream[IO, List[TwitterHistogram.JSON.HistogramPayload]] =
    listStream match {
      case Nil => Stream.empty
      case h::Nil => h.map(List(_))
      case h::t => h.zipWith(sequenceStreams(t))(_::_)
    }
  
  private def histogramsPayloadStream(histograms: List[TwitterHistogram]): Stream[IO, JSON.HistogramsPayload] = {
    val histogramPayloads: List[Stream[IO, TwitterHistogram.JSON.HistogramPayload]] =
      histograms.map(_.histogramPayloadStream)

    val sequenced = sequenceStreams(histogramPayloads)

    sequenced.map(JSON.makeHistogramsPayload)
  }
  
  val makeTwitterHistograms: IO[(Pipe[IO, Tweet, Tweet], Stream[IO, JSON.HistogramsPayload])] =
    makeHistograms.flatMap { histograms =>
      IO(println("histograms: "+histograms)).flatMap { _ =>
        makeConcatenatedHistogramPipe(histograms).map { pipe =>
          (pipe, histogramsPayloadStream(histograms))
        }
      }
    }


}
