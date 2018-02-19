package me.peterbecich.bannodemo.twitter.stats

import cats._
import cats.effect.{IO, Sync}
import cats.implicits._
import cats.syntax.all._
import com.danielasfregola.twitter4s.entities.Tweet
import fs2.{Stream, Pipe}
import java.util.concurrent.atomic.AtomicLong

/*
 https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/package-summary.html
 https://stackoverflow.com/questions/26902794/concurrency-primitives-in-scala
 https://twitter.github.io/scala_school/concurrency.html#danger
 */

object TwitterAccumulators {

  private def containsPic(tweet: Tweet): Boolean =
    tweet.text.contains("pbs.twimg.com") ||
      tweet.text.contains("abs.twimg.com")

  import me.peterbecich.bannodemo.emojis.Emojis.utf8EmojisChars

  def makeEmojisAccumulator(tweetsAccumulator: TwitterAccumulator): IO[TwitterAccumulator] = {
    def predicate(tweet: Tweet): Boolean =
      tweet.text.toCharArray().map(_.toString).exists { codepoint =>
        utf8EmojisChars.contains(codepoint)
      }

    TwitterAccumulator.makeAccumulator("EmojiAccumulator", predicate, Some(tweetsAccumulator))
  }

  /*
   Each accumulator must be constructed inside an effect type, specifically, `IO`.
   See TwitterAccumulator.scala

   Constructing a collection of accumulators can be done with a sequence of flatMaps,
   hidden under a for-comprehension here.

   While series of monadic flatMaps/binds is sequential, sequentiality is not necessary here.

   Predicates for the more-selective accumulators take the form of anonymous functions.
   */

  private lazy val makeAccumulators: IO[List[TwitterAccumulator]] =
    for {
      tweets <- TwitterAccumulator.makeAccumulator("TweetAccumulator", _ => true)
      urls <- TwitterAccumulator.makeAccumulator("URLAccumulator", tweet => tweet.text.contains("http"), Some(tweets))
      emojis <- makeEmojisAccumulator(tweets)
      pics <- TwitterAccumulator.makeAccumulator("PicAccumulator", containsPic, Some(tweets))
      hashtags <- TwitterAccumulator.makeAccumulator("HashtagAccumulator", tweet => tweet.text.contains("#"), Some(tweets))
    } yield List(tweets, emojis, urls, pics, hashtags)

  object JSON {
    import io.circe._
    import io.circe.Encoder
    import io.circe.syntax._
    import io.circe.literal._
    import io.circe.generic.semiauto._

    import TwitterAccumulator.JSON._

    /*
     This Map is serialized easily into a JSON map,
     keyed by the name of the accumulator.
     */
    type AccumulatorsPayload = Map[String, AccumulatorPayload]

    def makeAccumulatorsPayload(accumulators: List[TwitterAccumulator.JSON.AccumulatorPayload]):
        AccumulatorsPayload =
      accumulators.map { acc => (acc.name, acc) }.toMap

  }


  /*
   Each accumulator in `accumulators: List[TwitterAccumulator]` 
   provides a `Stream` of payloads -- completely separate from the `Pipe` of Tweets.
   Each of these streams emits a payload as frequently as the downstream code requests.
   
   See comments on `Signal` in TwitterAccumulator.scala

   This collection of `Streams` of payloads is `sequence`d into a single payload.

   Given
     List[Stream[IO, TwitterAccumulator.JSON.AccumulatorPayload]]
   sequence into
     Stream[IO, List[TwitterAccumulator.JSON.AccumulatorPayload]]
   then combine the `List` into a single payload
     Stream[IO, JSON.AccumulatorsPayload]
   */

  def accumulatorsPayloadStream(accumulators: List[TwitterAccumulator]):
      Stream[IO, JSON.AccumulatorsPayload] = {
    lazy val accumulatorPayloads: List[Stream[IO, TwitterAccumulator.JSON.AccumulatorPayload]] =
      accumulators
        .map(_.accumulatorPayloadStream)

    lazy val streamListPayload: Stream[IO, List[TwitterAccumulator.JSON.AccumulatorPayload]] =
      Traverse[List].sequence(accumulatorPayloads)

    streamListPayload.map(JSON.makeAccumulatorsPayload)
  }

  /*
   Each accumulator provides a `Pipe[IO, Tweet, Tweet]`.
   Tweets that pass through this `Pipe` and satisfy the accumulator's predicate are counted.

   This collection of `Pipe`s is combined by concatenation
   into a single `Pipe`.

   */

  def passThru[A]: Pipe[IO, A, A] = stream => stream

  def pipeConcatenationMonoid[A] = new Monoid[Pipe[IO, A, A]] {
    def combine(pipe1: Pipe[IO, A, A], pipe2: Pipe[IO, A, A]): Pipe[IO, A, A] =
      pipe1.andThen(pipe2)
    def empty: Pipe[IO, A, A] = passThru[A]
  }

  private def makeAccumulatorPipe(accumulators: List[TwitterAccumulator]):
      IO[Pipe[IO, Tweet, Tweet]] =
    IO(Foldable[List].foldMap(accumulators)(_.accumulatorPipe)(pipeConcatenationMonoid[Tweet]))


  /*
   As stated elsewhere, each `Signal` within these accumulators must be constructed inside
   an effect type, typically `IO`.

   Thus, all accumulators must be constructed inside an effect type --
   the `Pipe` through which Tweets are counted is constructed inside `IO`.

   
   */
  val makeTwitterAccumulator: IO[(Pipe[IO, Tweet, Tweet], Stream[IO, JSON.AccumulatorsPayload])] =
    makeAccumulators.flatMap { accumulators =>
      makeAccumulatorPipe(accumulators).map { pipe =>
        (pipe, accumulatorsPayloadStream(accumulators))
      }
    }


}

