package me.peterbecich.bannodemo.twitter.stats

import cats._
import cats.effect.{IO, Sync}
import cats.implicits._
import cats.syntax.all._
import com.danielasfregola.twitter4s.entities.Tweet
import fs2.async.immutable.{Signal => ISignal}
import fs2.async.mutable.Signal
import fs2.{Stream, Pipe}
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.ExecutionContext.Implicits.global

object TwitterAccumulator {

  private def makeCountSignal: IO[Signal[IO, Long]] =
    Signal.apply[IO, Long](0L)(IO.ioEffect, global)

  /*
   `Signal` and various other FS2 types can only be constructed inside an effect type.
   i.e. `Signal[IO, Int]` is constructed inside an IO -- IO[Signal[IO, Int]].
   
   This requirement is then imposed on the class that contains the `Signal` -- `TwitterAccumulator`.
   A `TwitterAccumulator` is constructed inside an effect type.
   */
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

    /*
     A simple DTO that is easily serialized to JSON
     */
    case class AccumulatorPayload(
      name: String,
      count: Long,
      percentage: Double
    )

    implicit val accumulatorPayloadEncoder: Encoder[AccumulatorPayload] = deriveEncoder

    /*
     Produce an `AccumulatorPayload` to be serialized to JSON.
     If this is the baseline `Accumulator`, there is no percentage to calculate.
     Otherwise, calculate percentage of this Tweet count to all Tweets for payload.
     */
    def makeAccumulatorPayload(accumulator: TwitterAccumulator):
        IO[AccumulatorPayload] =
      accumulator.ototal match {
        case None =>
          for {
            count <- accumulator.countSignal.get
          } yield AccumulatorPayload(accumulator.name, count, 1.0)
        case Some(totalAcc) =>
          for {
            count <- accumulator.countSignal.get
            total <- totalAcc.countSignal.get
          } yield AccumulatorPayload(accumulator.name, count, count.toDouble / total)
      }
  }
}

/*
 Counts Tweets that satisfy a predicate.
 Gives percentage of those Tweets relative to all Tweets.
 */
abstract class TwitterAccumulator {

  val name: String
  val predicate: Tweet => Boolean

  /*
   The count is held inside a `Signal`.
   
   Signal[F,A]
   typically, Signal[IO, A]

   The Signal must be constructed inside its effect type, typically IO.
   This is why the signal below is abstract.

   See https://oss.sonatype.org/service/local/repositories/releases/archive/co/fs2/fs2-core_2.12/0.10.1/fs2-core_2.12-0.10.1-javadoc.jar/!/fs2/async/mutable/Signal$.html
   Signal.apply[F[_], A](initial: A): F[Signal[F, A]]
   
   */
  val countSignal: Signal[IO, Long]

  /*
   The percentage of this Tweet count relative to all Tweets is calculated with
   reference to the "baseline" accumulator.
   If `this` is the "baseline" accumulator, `ototal` is `None`.
   */
  val ototal: Option[TwitterAccumulator]

  /*
   There are several ways to retrieve the value[s] inside a `Signal`.
   https://oss.sonatype.org/service/local/repositories/releases/archive/co/fs2/fs2-core_2.12/0.10.1/fs2-core_2.12-0.10.1-javadoc.jar/!/fs2/async/mutable/Signal.html
   */
  def getCount: IO[Long] = countSignal.get

  /*
   The `Signal` instance method `continuous` returns a `Stream` that emits a value upon every request.
   The `Signal` instance method `discrete` returns a `Stream` that emits a value only when the contents of the `Signal` change.

   Method `description` returns a `Stream` describing the current count.
   */
  lazy val description: Stream[IO, String] =
    countSignal.continuous.map(i => name + ": " + i)

  def describe: IO[String] = getCount.map(i => name + ": " + i)

  /*
   This IO is run for every Tweet that comes through the `Pipe`
   and which satisfies the `predicate`
   */
  def increment: IO[Unit] = countSignal
    .modify(_+1)
    .flatMap { _ => countSignal.get }
    .flatMap { n =>
      if (n % 1000 != 0)
        IO (())
      else
        describe.flatMap { s => IO ( println(s) ) }
    }.map(_ => ())

  /*
   A `Stream` that emits the most recently calculated percentage of
   this accumulator over the baseline accumulator.
   */
  lazy val percentage: Stream[IO, Double] = ototal match {
    case None => Stream.constant(1.0, 1)
    case Some(total) => total
        .countSignal
        .continuous
        .zip(countSignal.continuous)
        .map { case (total, count) => count.toDouble / total }
  } 

  lazy val accumulatorPayloadStream:
      Stream[IO, TwitterAccumulator.JSON.AccumulatorPayload] =
    Stream.repeatEval(TwitterAccumulator.JSON.makeAccumulatorPayload(this))

  /*
   Pipeline that receives and outputs Tweets.
   If a Tweet satisfies the predicate, 
   the count is incremented; percentage is updated.
   Otherwise, the `Pipe` is a simple pass-through.
   */
  val accumulatorPipe: Pipe[IO, Tweet, Tweet] =
    (input: Stream[IO, Tweet]) => input.flatMap { tweet =>
      if (predicate(tweet)) {
        Stream.eval(increment).flatMap { (_: Unit) => Stream.emit(tweet) }
      } else Stream.emit(tweet)
    }
}
