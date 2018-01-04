package me.peterbecich.bannodemo.twitter

import org.scalacheck._
import org.scalacheck.Arbitrary._

import org.scalatest._
import org.scalatest.prop._
import org.scalatest.Matchers._

import java.util.Date

import scala.collection.Map

import com.danielasfregola.twitter4s.entities.Tweet

import me.peterbecich.bannodemo.twitter.TwitterSource._

import scala.concurrent.ExecutionContext.Implicits.global

import fs2._
import fs2.async.mutable.Queue

import cats._
import cats.effect.{IO, Sync}

class TwitterSourceSpec extends PropSpec with PropertyChecks with Matchers {
  import TwitterSourceGen._

  implicit override val generatorDrivenConfig =
    PropertyCheckConfig(minSize = 100, maxSize = 500)
  

  val tweetPrintSink: Sink[IO, Tweet] =
    (s: Stream[IO, Tweet]) => s
      .map(_.id_str)
      .through(fs2.text.utf8Encode)
      .through(fs2.io.stdout).drain

  property("all Tweets in Stream have fewer than 280 characters") {
    // https://oss.sonatype.org/service/local/repositories/releases/archive/co/fs2/fs2-core_2.12/0.10.0-M10/fs2-core_2.12-0.10.0-M10-javadoc.jar/!/fs2/Stream.html#forall(p:O=%3EBoolean):fs2.Stream[F,Boolean]
    // https://oss.sonatype.org/service/local/repositories/releases/archive/co/fs2/fs2-core_2.12/0.10.0-M8/fs2-core_2.12-0.10.0-M8-javadoc.jar/!/fs2/Stream$$InvariantOps.html
    forAll { (stream: Stream[IO, Tweet]) =>

      // val b = stream.observe(tweetPrintSink).forall(tweet => tweet.text.length() <= 280).runLast(IO.ioEffect).unsafeRunSync()
      
      val b = stream.forall(tweet => tweet.text.length() <= 280).runLast(IO.ioEffect).unsafeRunSync()
      b should equal (Some(true))
    }
  }

  property("Tweet Stream holds more than 0 Tweets") {
    forAll { (stream: Stream[IO, Tweet]) =>
      val getCount: IO[Int] = stream
        .map(_ => 1)
        .runFold(0){ (s, _) => s + 1 }

      val count = getCount.unsafeRunSync()
      // println("count: "+count)
      count should be > 0
    }
  }



  // property("Twitter Source holds Tweets pushed into it") {
  //   forAll { (stream: Stream[IO, Tweet]) =>
  //     val fullQueue: IO[Int] =
  //       createTwitterQueue.flatMap { twitterQueue =>
  //         stream.observe(twitterQueue.enqueue).drain.run.flatMap { _ =>
  //           twitterQueue.size.get
  //         }
  //       }


  //     fullQueue.unsafeRunSync() should be >= 0

  //   }
  // }

}
