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

  private lazy val makeAccumulators: IO[List[TwitterAccumulator]] =
    for {
      tweets <- TwitterAccumulator.makeAccumulator("TweetAccumulator", _ => true)
      emojis <- TwitterAccumulator.makeAccumulator("EmojiAccumulator", _ => true, Some(tweets))
      urls <- TwitterAccumulator.makeAccumulator("URLAccumulator", tweet => tweet.text.contains("http"), Some(tweets))
      pics <- TwitterAccumulator.makeAccumulator("PicAccumulator", _ => true, Some(tweets))
      hashtags <- TwitterAccumulator.makeAccumulator("HashtagAccumulator", tweet => tweet.text.contains("#"), Some(tweets))
    } yield List(tweets, emojis, urls, pics, hashtags)

  def passThru[A]: Pipe[IO, A, A] = stream => stream

  def pipeConcatenationMonoid[A] = new Monoid[Pipe[IO, A, A]] {
    def combine(pipe1: Pipe[IO, A, A], pipe2: Pipe[IO, A, A]): Pipe[IO, A, A] =
      pipe1.andThen(pipe2)
    def empty: Pipe[IO, A, A] = passThru[A]
  }

  val makeAccumulator: IO[Pipe[IO, Tweet, Tweet]] =
    makeAccumulators.map { accumulators =>
      Foldable[List].foldMap(accumulators)(_.accumulatorPipe)(pipeConcatenationMonoid[Tweet])
    }




  object JSON {
    import io.circe._
    import io.circe.Encoder
    import io.circe.syntax._
    import io.circe.literal._
    import io.circe.generic.semiauto._

    import TwitterAccumulator.JSON._

    type AccumulatorsPayload = Map[String, AccumulatorPayload]


  }

}


// object TwitterAccumulatorExample {
//   import fs2.{io, text}
//   import TwitterAccumulators.concatenatedAccumulatorPipes
//   import TwitterAccumulators.accumulators
//   import TwitterAccumulators.TweetCount
//   import scala.concurrent.ExecutionContext.Implicits.global

//   val countTwitter: IO[Unit] = TwitterQueue.createTwitterStream.flatMap { twitterStream =>
//     twitterStream
//       .through(concatenatedAccumulatorPipes)
//       .take(2048)
//       .map(t => t.user.map(_.name).getOrElse("nobody"))
//       .intersperse("\n")
//       .through(text.utf8Encode)
//       .observe(io.stdout)
//       .drain.run
//   }

//   def main(args: Array[String]): Unit = {
//     println("twitter queue example, with FS2")
//     countTwitter.unsafeRunSync()
//     println("---------------------------------")
//     accumulators.foreach { accumulator => println(accumulator.describe) }
//   }
// }
