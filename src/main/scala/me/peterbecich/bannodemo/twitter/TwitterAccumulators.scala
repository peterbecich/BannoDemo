package me.peterbecich.bannodemo.twitter

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

 */

object TwitterAccumulators {

  abstract class TwitterAccumulator {

    def predicate: Tweet => Boolean

    private val count: AtomicLong = new AtomicLong(0);

    def getCount: Long = count.get()

    val name: String
    def describe: String = name + ": " + getCount.toString

    val accumulatorPipe: Pipe[IO, Tweet, Tweet] =
      (input: Stream[IO, Tweet]) => input.flatMap { tweet =>
        // TODO do this in IO
        // https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/AtomicLong.html
        if (predicate(tweet))
          count.incrementAndGet()
        input
      }
  }

  case object TweetCount extends TwitterAccumulator {
    def predicate: Tweet => Boolean = _ => true
    val name = "TweetCount"
  }

  case object EmojiTweetCount extends TwitterAccumulator {
    def predicate: Tweet => Boolean = _ => true // TODO emoji predicate
    val name = "EmojiTweetCount"
  }

  val accumulators = List(TweetCount, EmojiTweetCount)


  val passThru: Pipe[IO, Tweet, Tweet] = stream => stream

  // all pipes concatenated together
  // https://typelevel.org/cats/api/cats/Foldable.html#foldM[G[_],A,B](fa:F[A],z:B)(f:(B,A)=%3EG[B])(implicitG:cats.Monad[G]):G[B]
  // val concatenatedAccumulatorPipes: Pipe[IO, Tweet, Tweet] =
  //   Foldable[List].foldM(accumulators, passThru)(

  // val concatenatedAccumulatorPipes: Pipe[IO, Tweet, Tweet] =
  //   Foldable[List].foldMap(accumulators)(_.accumulatorPipe)

  val concatenatedAccumulatorPipes: Pipe[IO, Tweet, Tweet] =
    TweetCount.accumulatorPipe.andThen(EmojiTweetCount.accumulatorPipe)
}


object TwitterAccumulatorExample {
  import fs2.{io, text}
  import TwitterAccumulators.concatenatedAccumulatorPipes
  import TwitterAccumulators.accumulators
  import scala.concurrent.ExecutionContext.Implicits.global
  

  val countTwitter: IO[Unit] = TwitterQueue.createTwitterStream.flatMap { twitterStream =>
    twitterStream
      .through(concatenatedAccumulatorPipes)
      .take(512)
      .map(t => t.user.map(_.name).getOrElse("nobody"))
      .intersperse("\n")
      .through(text.utf8Encode)
      .observe(io.stdout)
      .drain.run
  }

  def main(args: Array[String]): Unit = {
    println("twitter queue example, with FS2")

    countTwitter.unsafeRunSync()


    println("---------------------------------")

    accumulators.foreach { accumulator => println(accumulator.describe) }

  }


}
