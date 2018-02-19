package me.peterbecich.bannodemo.twitter.stats

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
import me.peterbecich.bannodemo.emojis.Emojis._
import scala.collection.concurrent.TrieMap
import scala.collection.immutable.IndexedSeq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object TwitterHistograms {

  private lazy val urlHistogram: IO[TwitterHistogram] =
    TwitterHistogram.makeTwitterHistogramRegex("URL", raw"http\S+\.(com|org|net|co|c)?".r)

  private lazy val urlEndpointHistogram: IO[TwitterHistogram] =
    TwitterHistogram.makeTwitterHistogramRegex("URL Endpoint", raw"http\S+\.(com|org|net|co|c)/\S+".r)
  
  private lazy val hashtagHistogram: IO[TwitterHistogram] =
    TwitterHistogram.makeTwitterHistogramRegex("Hashtag", raw"""\#\S+""".r)

  private lazy val emojisHistogram: IO[TwitterHistogram] =
    retrieveEmojis match {
      case Left(error) => {
        println("error retrieving emojis from disk")
        TwitterHistogram.makeTwitterHistogram("Emojis", (_: Tweet) => Seq(), _bins = IndexedSeq.empty[String], _growBins = false)
      }
      case Right(emojis) => {
        val bins: IndexedSeq[String] = emojis
          .map(_.emojiChar)
          .collect {
            case Some( emojiChar ) => emojiChar
          }
          .map(_.toString)
          .toIndexedSeq

        /*
         "Visual" check that emojis are loaded from text file on disk.
         */

        println("a few emoji codepoints:")
        bins.take(128).foreach(s => print(s + " "))

        def keys(tweet: Tweet): IndexedSeq[String] =
          _keys(tweet.text)

        def _keys(tweetText: String): IndexedSeq[String] = {
          val tweetCharStrings = tweetText.toCharArray().map(_.toString)
          val product = bins.flatMap { emojiCharS =>
            tweetCharStrings.map { tweetCharS =>
              (emojiCharS, tweetCharS)
            }
          }

          product
            .filter { case (e, t) => e.equalsIgnoreCase(t) }
            .map(_._1)
        }

        val testTweet = "test tweet 🎶 👂 🤑 🎒 💛 😂 👏 🐼 📸 💕 ☔ ☕ ☘ ☝ ☠ ☢ ☣ ☦ ☪ ☮ ☯ ☸ ☹ ☺ foobar"

        lazy val testTweetKeys = _keys(testTweet)

        println("test tweet")
        println(testTweet)
        println("test tweet keys found")
        println(testTweetKeys)
        
        TwitterHistogram.makeTwitterHistogram("UTF8Emojis", keys, _bins = bins, _growBins = false)

      }
  }

  private lazy val makeHistograms: IO[List[TwitterHistogram]] =
    Traverse[List].sequence(List(urlHistogram, urlEndpointHistogram, hashtagHistogram, emojisHistogram))

  object JSON {

    import cats.instances.map._
    import io.circe.Encoder
    import io.circe._
    import io.circe.generic.semiauto._
    import io.circe.literal._
    import io.circe.syntax._
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
