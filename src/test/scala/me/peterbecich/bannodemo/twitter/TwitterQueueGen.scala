package me.peterbecich.bannodemo.twitter

import org.scalacheck._
import org.scalacheck.Arbitrary._

import org.scalatest._
import org.scalatest.prop._
import org.scalatest.Matchers._

import java.util.Date

import scala.collection.Map

import com.danielasfregola.twitter4s.entities.Tweet

import me.peterbecich.bannodemo.twitter.TwitterQueue._

// import scala.concurrent.ExecutionContext.Implicits.global

object TwitterQueueGen {
  import TweetGen._

  import fs2._
  import fs2.async.mutable.Queue

  import cats._
  import cats.effect.{IO, Sync}

  val twitterStreamGen: Gen[Stream[IO, Tweet]] = for {
    seqTweets <- arbitrary[Seq[Tweet]]
  } yield Stream.emits(seqTweets)

  implicit val twitterStreamArbitrary: Arbitrary[Stream[IO, Tweet]] =
    Arbitrary(twitterStreamGen)

}
