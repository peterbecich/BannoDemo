package me.peterbecich.bannodemo.twitter

import java.util.concurrent.atomic.AtomicLong

import com.danielasfregola.twitter4s.entities.Tweet

import cats._
import cats.implicits._
import cats.syntax.all._
import cats.effect.{IO, Sync}
import fs2._

/*
 https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/package-summary.html
 https://stackoverflow.com/questions/26902794/concurrency-primitives-in-scala
 https://twitter.github.io/scala_school/concurrency.html#danger
 */

object TwitterAccumulators {

  abstract class TwitterAccumulator {

    val name: String
    val predicate: Tweet => Boolean

    private val count: AtomicLong = new AtomicLong(0);

    def getCount: IO[Long] = IO(count.get())
    def describe: IO[String] = getCount.map(i => name + ": " + i)
    def increment: IO[Unit] = IO(count.incrementAndGet()).flatMap { n =>
      if (n % 1000 != 0)
        IO (())
      else
        describe.flatMap { s => IO ( println(s) ) }
    }

    def getPercentage: IO[Double] = for {
      tweetCount <- TweetCount.getCount
      accCount <- getCount
    } yield accCount.toDouble / tweetCount

    // TODO do this in IO
    // https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/AtomicLong.html
    
    def accumulatorPipe: Pipe[IO, Tweet, Tweet] =
      (input: Stream[IO, Tweet]) => input.flatMap { tweet =>
        if (predicate(tweet)) {
          Stream.eval(increment).flatMap { (_: Unit) => Stream.emit(tweet) }
        } else Stream.emit(tweet)
      }
  }

  case object TweetCount extends TwitterAccumulator {
    val predicate: Tweet => Boolean = _ => true
    val name = "TweetCount"
  }

  case object EmojiTweetCount extends TwitterAccumulator {
    val predicate: Tweet => Boolean = _ => true // TODO emoji predicate
    val name = "EmojiTweetCount"
  }

  case object URLTweetCount extends TwitterAccumulator {
    val predicate: Tweet => Boolean =
      tweet => tweet.text.contains("http")
    val name = "URLTweetCount"
  }

  case object PicTweetCount extends TwitterAccumulator {
    val predicate: Tweet => Boolean =
      tweet => tweet.text.contains("pbs.twimg.com") ||
        tweet.text.contains("pic.twitter.com") ||
        tweet.text.contains("www.instagram.com") ||
        tweet.text.contains("insta.gram")
    val name = "PicTweetCount"
  }

  case object HashtagTweetCount extends TwitterAccumulator {
    val predicate: Tweet => Boolean =
      tweet => tweet.text.contains("#")
    val name = "HashtagTweetCount"
  }
  
  val accumulators = List(TweetCount, EmojiTweetCount, URLTweetCount, PicTweetCount, HashtagTweetCount)


  def passThru[A]: Pipe[IO, A, A] = stream => stream

  // all pipes concatenated together
  // https://typelevel.org/cats/api/cats/Foldable.html#foldM[G[_],A,B](fa:F[A],z:B)(f:(B,A)=%3EG[B])(implicitG:cats.Monad[G]):G[B]
  // val concatenatedAccumulatorPipes: Pipe[IO, Tweet, Tweet] =
  //   Foldable[List].foldM(accumulators, passThru)(

  def pipeConcatenationMonoid[A] = new Monoid[Pipe[IO, A, A]] {
    def combine(pipe1: Pipe[IO, A, A], pipe2: Pipe[IO, A, A]): Pipe[IO, A, A] =
      pipe1.andThen(pipe2)
    def empty: Pipe[IO, A, A] = passThru[A]
  }

  val concatenatedAccumulatorPipes: Pipe[IO, Tweet, Tweet] =
    Foldable[List].foldMap(accumulators)(_.accumulatorPipe)(pipeConcatenationMonoid[Tweet])

  // val concatenatedAccumulatorPipes: Pipe[IO, Tweet, Tweet] =
  //   TweetCount.accumulatorPipe.andThen(EmojiTweetCount.accumulatorPipe)

  val accumulateTwitter: IO[Unit] = TwitterQueue.createTwitterStream.flatMap { twitterStream =>
    twitterStream
      .through(concatenatedAccumulatorPipes)
      .drain.run
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
//       // .observe(io.stdout)
//       .drain.run
//   }

//   def main(args: Array[String]): Unit = {
//     println("twitter queue example, with FS2")
//     countTwitter.unsafeRunSync()
//     println("---------------------------------")
//     accumulators.foreach { accumulator => println(accumulator.describe) }
//   }
// }
