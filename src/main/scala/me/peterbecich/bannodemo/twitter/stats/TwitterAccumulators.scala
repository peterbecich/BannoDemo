package me.peterbecich.bannodemo.twitter.stats

import java.util.concurrent.atomic.AtomicLong

import com.danielasfregola.twitter4s.entities.Tweet

import cats._
import cats.implicits._
import cats.syntax.all._
import cats.effect.{IO, Sync}
import fs2.{Stream, Pipe}

/*
 https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/package-summary.html
 https://stackoverflow.com/questions/26902794/concurrency-primitives-in-scala
 https://twitter.github.io/scala_school/concurrency.html#danger
 */

object TwitterAccumulators {

  private def containsPic(tweet: Tweet): Boolean =
    tweet.text.contains("pbs.twimg.com") ||
      tweet.text.contains("abs.twimg.com")

  private lazy val makeAccumulators: IO[List[TwitterAccumulator]] =
    for {
      tweets <- TwitterAccumulator.makeAccumulator("TweetAccumulator", _ => true)
      emojis <- TwitterAccumulator.makeAccumulator("EmojiAccumulator", tweet => true,Some(tweets))
      urls <- TwitterAccumulator.makeAccumulator("URLAccumulator", tweet => tweet.text.contains("http"), Some(tweets))
      pics <- TwitterAccumulator.makeAccumulator("PicAccumulator", containsPic, Some(tweets))
      hashtags <- TwitterAccumulator.makeAccumulator("HashtagAccumulator", tweet => tweet.text.contains("#"), Some(tweets))
    } yield List(tweets, emojis, urls, pics, hashtags)


  def accumulatorsPayloadStream(accumulators: List[TwitterAccumulator]):
      Stream[IO, JSON.AccumulatorsPayload] = {
    lazy val accumulatorPayloads: List[Stream[IO, TwitterAccumulator.JSON.AccumulatorPayload]] =
      accumulators
        .map(_.accumulatorPayloadStream)

    lazy val streamListPayload: Stream[IO, List[TwitterAccumulator.JSON.AccumulatorPayload]] =
      Traverse[List].sequence(accumulatorPayloads)

    streamListPayload.map(JSON.makeAccumulatorsPayload)
  }




  def passThru[A]: Pipe[IO, A, A] = stream => stream

  def pipeConcatenationMonoid[A] = new Monoid[Pipe[IO, A, A]] {
    def combine(pipe1: Pipe[IO, A, A], pipe2: Pipe[IO, A, A]): Pipe[IO, A, A] =
      pipe1.andThen(pipe2)
    def empty: Pipe[IO, A, A] = passThru[A]
  }

  private def makeAccumulatorPipe(accumulators: List[TwitterAccumulator]):
      IO[Pipe[IO, Tweet, Tweet]] =
    IO(Foldable[List].foldMap(accumulators)(_.accumulatorPipe)(pipeConcatenationMonoid[Tweet]))

  val makeTwitterAccumulator: IO[(Pipe[IO, Tweet, Tweet], Stream[IO, JSON.AccumulatorsPayload])] =
    makeAccumulators.flatMap { accumulators =>
      makeAccumulatorPipe(accumulators).map { pipe =>
        (pipe, accumulatorsPayloadStream(accumulators))
      }
    }




  object JSON {
    import io.circe._
    import io.circe.Encoder
    import io.circe.syntax._
    import io.circe.literal._
    import io.circe.generic.semiauto._

    import TwitterAccumulator.JSON._

    type AccumulatorsPayload = Map[String, AccumulatorPayload]

    def makeAccumulatorsPayload(accumulators: List[TwitterAccumulator.JSON.AccumulatorPayload]):
        AccumulatorsPayload =
      accumulators.map { acc => (acc.name, acc) }.toMap

  }

}

