package me.peterbecich.bannodemo.twitter.stats.examples

import cats._
import cats.effect.{IO, Sync}
import cats.implicits._
import cats.syntax.all._
import com.danielasfregola.twitter4s.entities.Tweet
import fs2.{Stream, Pipe}
import java.util.concurrent.atomic.AtomicLong

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
