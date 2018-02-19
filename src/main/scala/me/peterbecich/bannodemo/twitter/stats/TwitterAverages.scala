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
import me.peterbecich.bannodemo.twitter.TwitterStats.getTweetTime
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object TwitterAverages {

  private lazy val tweetAverage: IO[TwitterAverage] =
    TwitterAverage.makeAverage("TweetAverage", (_) => true)
  private lazy val hashtagAverage: IO[TwitterAverage] =
    TwitterAverage.makeAverage("HashtagAverage", tweet => tweet.text.contains("#"))

  import me.peterbecich.bannodemo.emojis.Emojis._
  
  private lazy val emojiAverage: IO[TwitterAverage] =
    retrieveEmojis match {
      case Left(error) => {
        println("error retrieving emojis from disk")
        TwitterAverage.makeAverage("EmojiAverage", (_) => false)
      }
      case Right(emojis) => {
        val bins: IndexedSeq[String] = emojis
          .map(_.emojiChar)
          .collect {
            case Some( emojiChar ) => emojiChar
          }
          .map(_.toString)
          .toIndexedSeq

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
          // println("product: "+product.length)

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

        val testTweet2 = "test tweet no emojis"

        lazy val testTweetKeys2 = _keys(testTweet2)

        println("test tweet 2")
        println(testTweet2)
        println("test tweet 2 keys found")
        println(testTweetKeys2)
        
        
        TwitterAverage.makeAverage("EmojiAverage", tweet => (keys(tweet).length > 0))
      }
    }

  object JSON {
    import io.circe._
    import io.circe.Encoder
    import io.circe.syntax._
    import io.circe.literal._
    import io.circe.generic.semiauto._

    import cats.instances.map._

    import scala.collection.immutable.HashMap

    import me.peterbecich.bannodemo.JSON.Common._
    import me.peterbecich.bannodemo.twitter.stats.TwitterAverage.JSON._

    type AveragesPayload = Map[String, AveragePayload]

    // implicit val averagesPayloadEncoder: Encoder[AveragesPayload] = deriveEncoder

    def makeAveragesPayload(averages: List[AveragePayload]): AveragesPayload =
      averages.map { avePayload =>
        // println("make averages payload: "+avePayload)
        (avePayload.name, avePayload)
      }.toMap

    def makeAveragesPayloadJson(averages: List[AveragePayload]): Json =
      makeAveragesPayload(averages).asJson

  }


  private lazy val makeAverages: IO[List[TwitterAverage]] =
    Traverse[List].sequence(List(tweetAverage, emojiAverage, hashtagAverage))

  private def passThru[A]: Pipe[IO, A, A] = stream => stream

  private def pipeConcatenationMonoid[A] = new Monoid[Pipe[IO, A, A]] {
    def combine(pipe1: Pipe[IO, A, A], pipe2: Pipe[IO, A, A]): Pipe[IO, A, A] =
      pipe1.andThen(pipe2)
    def empty: Pipe[IO, A, A] = passThru[A]
  }

  def makeConcatenatedAveragePipe(averages: List[TwitterAverage]): IO[Pipe[IO, Tweet, Tweet]] =
    IO(Foldable[List].foldMap(averages)(_.averagePipe)(pipeConcatenationMonoid))

  import cats.instances.map._
  
  import scala.collection.immutable.HashMap

  // GADT skolem error
  // def sequenceStreams[F[_],O](listStream: List[Stream[F,O]]): Stream[F, List[O]] =
  //   listStream match {
  //     case Nil => Stream.empty
  //     case h::Nil => h.map(List(_))
  //     case h::t => ???
  //   }

  def sequenceStreams(listStream: List[Stream[IO,TwitterAverage.JSON.AveragePayload]]):
      Stream[IO, List[TwitterAverage.JSON.AveragePayload]] =
    listStream match {
      case Nil => Stream.empty
      case h::Nil => h.map(List(_))
      case h::t => h.zipWith(sequenceStreams(t))(_::_)
    }
  
  def averagesPayloadStream(averages: List[TwitterAverage]): Stream[IO, JSON.AveragesPayload] = {
    val averagePayloads: List[Stream[IO, TwitterAverage.JSON.AveragePayload]] =
      averages.map(_.averagePayloadStream)

    val streamListPayload: Stream[IO, List[TwitterAverage.JSON.AveragePayload]] =
      sequenceStreams(averagePayloads)

    streamListPayload.map(JSON.makeAveragesPayload)
  }

  val makeTwitterAverages: IO[(Pipe[IO, Tweet, Tweet], Stream[IO, JSON.AveragesPayload])] =
    makeAverages.flatMap { averages =>
      makeConcatenatedAveragePipe(averages).map { pipe =>
        (pipe, averagesPayloadStream(averages))
      }
    }
}

