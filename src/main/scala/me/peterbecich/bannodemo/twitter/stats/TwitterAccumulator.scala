package me.peterbecich.bannodemo.twitter.stats

import cats._
import cats.effect.{IO, Sync}
import cats.implicits._
import cats.syntax.all._
import com.danielasfregola.twitter4s.entities.Tweet
import fs2._
import java.util.concurrent.atomic.AtomicLong

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
    tweetCount <- TwitterAccumulators.TweetCount.getCount
    accCount <- getCount
  } yield accCount.toDouble / tweetCount

  // TODO do this in IO
  // https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/AtomicLong.html

  // TODO make val?
  val accumulatorPipe: Pipe[IO, Tweet, Tweet] =
    (input: Stream[IO, Tweet]) => input.flatMap { tweet =>
      if (predicate(tweet)) {
        Stream.eval(increment).flatMap { (_: Unit) => Stream.emit(tweet) }
      } else Stream.emit(tweet)
    }
}
