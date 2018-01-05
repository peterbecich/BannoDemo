package me.peterbecich.bannodemo.twitter.stats

import cats._
import cats.effect.{IO, Sync}
import cats.implicits._
import cats.syntax.all._
import com.danielasfregola.twitter4s.entities.Tweet
import fs2.{Stream, Pipe}
import fs2.async.mutable.Signal
import fs2.async.immutable.{Signal => ISignal}
import java.util.concurrent.atomic.AtomicLong

import scala.concurrent.ExecutionContext.Implicits.global

object TwitterAccumulator {

  private def makeCountSignal: IO[Signal[IO, Long]] =
    Signal.apply[IO, Long](0L)(IO.ioEffect, global)

  def makeAccumulator(_name: String,
    _predicate: Tweet => Boolean,
    _total: Option[TwitterAccumulator] = None): IO[TwitterAccumulator] =
    makeCountSignal.map { _countSignal =>
      new TwitterAccumulator {
        val name = _name
        val predicate = _predicate
        val countSignal = _countSignal
        val ototal = _total
      }
    }



  object JSON {
    import io.circe._
    import io.circe.Encoder
    import io.circe.syntax._
    import io.circe.literal._
    import io.circe.generic.semiauto._

    case class AccumulatorPayload(
      name: String,
      count: Long,
      percentage: Double
    )

    implicit val accumulatorPayloadEncoder: Encoder[AccumulatorPayload] = deriveEncoder

    def makeAccumulatorPayload(accumulator: TwitterAccumulator):
        IO[AccumulatorPayload] = ???
      // for {
      //   count <- accumulator.getCount
      //   percentage <- accumulator.
      // } yield AccumulatorPayload(accumulator.name, count, percentage)


  }

}

/*
 Counts Tweets that satisfy a given predicate.
 Gives percentage of those Tweets relative to all Tweets.
 */

abstract class TwitterAccumulator {

  val name: String
  val predicate: Tweet => Boolean

  val countSignal: Signal[IO, Long]

  val ototal: Option[TwitterAccumulator]

  def getCount: IO[Long] = countSignal.get

  lazy val description: Stream[IO, String] =
    countSignal.continuous.map(i => name + ": " + i)

  def describe: IO[String] = getCount.map(i => name + ": " + i)

  def increment: IO[Unit] = countSignal
    .modify(_+1)
    .flatMap { _ => countSignal.get }
    .flatMap { n =>
      if (n % 1000 != 0)
        IO (())
      else
        describe.flatMap { s => IO ( println(s) ) }
    }.map(_ => ())

  lazy val percentage: Stream[IO, Double] = ototal match {
    case None => Stream.constant(1.0, 1)
    case Some(total) => total
        .countSignal
        .continuous
        .zip(countSignal.continuous)
        .map { case (total, count) => count.toDouble / total }
  }

  val accumulatorPipe: Pipe[IO, Tweet, Tweet] =
    (input: Stream[IO, Tweet]) => input.flatMap { tweet =>
      if (predicate(tweet)) {
        Stream.eval(increment).flatMap { (_: Unit) => Stream.emit(tweet) }
      } else Stream.emit(tweet)
    }
}
