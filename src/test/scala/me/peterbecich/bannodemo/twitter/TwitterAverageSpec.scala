package me.peterbecich.bannodemo.twitter

import org.scalacheck._
import org.scalacheck.Arbitrary._

import org.scalatest._
import org.scalatest.prop._
import org.scalatest.Matchers._

import java.util.Date

import scala.collection.Map

import com.danielasfregola.twitter4s.entities.Tweet

import scala.concurrent.ExecutionContext.Implicits.global

import fs2._
import fs2.async.mutable.Queue

import cats._
import cats.effect.{IO, Sync}

import TwitterAverage._

class TwitterAverageSpec extends PropSpec with PropertyChecks with Matchers {
  import TweetGen._
  import TwitterQueueGen._

  implicit override val generatorDrivenConfig =
    PropertyCheckConfig(minSize = 100, maxSize = 500)

  property("dummy test") {
    forAll { (tweets: Stream[IO, Tweet]) =>
      val makeTweetAverage: IO[TwitterAverage] = TwitterAverage.makeAverage("TweetAverage", (_) => true)

      val triemap: IO[TimeTable] =
        makeTweetAverage.flatMap { tweetAverage =>
          tweets.through(tweetAverage.averagePipe).drain.run.flatMap { _ =>
            tweetAverage.timeTableSignal.get
          }
        }

      triemap.unsafeRunSync.size should be >= 0

    }
  }

  property("Tweets pass through averaging pipeline") {
    forAll { (tweets: Stream[IO, Tweet]) =>
      val makeTweetAverage: IO[TwitterAverage] = TwitterAverage.makeAverage("TweetAverage", (_) => true)

      val getCount: IO[Int] =
        makeTweetAverage.flatMap { tweetAverage =>
          tweets.through(tweetAverage.averagePipe).map(_ => 1).runFold(0) { (s, _) => s+1 }
        }

      val count = getCount.unsafeRunSync()
      // println("count: "+count)
      count should be > 0

    }
  }



}






