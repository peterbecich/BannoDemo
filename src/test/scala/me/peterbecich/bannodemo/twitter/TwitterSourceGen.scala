package me.peterbecich.bannodemo.twitter

import org.scalacheck._
import org.scalacheck.Arbitrary._

import org.scalatest._
import org.scalatest.prop._
import org.scalatest.Matchers._

import cats.effect.IO

import java.util.Date

import scala.collection.Map

import com.danielasfregola.twitter4s.entities.Tweet

import me.peterbecich.bannodemo.twitter.TwitterSource._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/*
 https://oss.sonatype.org/service/local/repositories/releases/archive/co/fs2/fs2-core_2.12/0.10.0-M8/fs2-core_2.12-0.10.0-M8-javadoc.jar/!/fs2/Scheduler.html

 */

object TwitterSourceGen {
  import TweetGen._

  import fs2._
  import fs2.async.mutable.Queue

  import cats._
  import cats.effect.{IO, Sync}

  val twitterStreamGen: Gen[Stream[IO, Tweet]] = for {
    // seqTweets <- arbitrary[Seq[Tweet]]
    n <- Gen.choose(64, 1024)
    seqTweets <- Gen.listOfN(n, arbitrary[Tweet])
  } yield Stream.emits(seqTweets)

  implicit val twitterStreamArbitrary: Arbitrary[Stream[IO, Tweet]] =
    Arbitrary(twitterStreamGen)

  val slowTwitterStreamGen: Gen[Stream[IO, Tweet]] = for {
    n <- Gen.choose(64, 1024)
    seqTweets <- Gen.listOfN(n, arbitrary[Tweet])
  } yield fs2.Scheduler.apply[IO](2).flatMap { scheduler =>
    Stream.covaryPure[IO, Tweet, Tweet](Stream.emits(seqTweets)).flatMap { tweet =>
      scheduler.delay(Stream.eval(IO(tweet)), 100.millisecond)
    }
  }

  def slowStream[O](stream: Stream[IO, O], delay: FiniteDuration = 500.millisecond): Stream[IO, O] =
    fs2.Scheduler.apply[IO](2).flatMap { scheduler =>
      stream.flatMap { element =>
        scheduler.delay(Stream.eval(IO(element)), delay)
      }
    }


  val oldTwitterStreamGen: Gen[Stream[IO, Tweet]] = for {
    // seqTweets <- arbitrary[Seq[Tweet]]
    n <- Gen.choose(64, 1024)
    seqTweets <- Gen.listOfN(n, oldTweetGen)
  } yield Stream.emits(seqTweets)

}
