package me.peterbecich.bannodemo.twitter

import cats._
import cats.effect.{IO, Sync}
import cats.syntax.all._
import com.danielasfregola.twitter4s.TwitterStreamingClient
import com.danielasfregola.twitter4s.entities.Tweet
import com.danielasfregola.twitter4s.entities.streaming.{StreamingMessage, CommonStreamingMessage, UserStreamingMessage, SiteStreamingMessage}
import fs2._
import fs2.async.mutable.Queue
import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import scala.concurrent.ExecutionContext.Implicits.global

/*
 Twitter4s
 https://github.com/DanielaSfregola/twitter4s

 Functional Streams for Scala
 https://github.com/functional-streams-for-scala/fs2
 */

object TwitterSource {

  val streamingClient = TwitterStreamingClient()

  val createTwitterQueue: IO[Queue[IO, Tweet]] = Queue.circularBuffer(1024)

  // Twitter4s library requires a Sink[IO, StreamingMessage]
  def streamingMessageEnqueue(tweetSink: Sink[IO, Tweet]): Sink[IO, StreamingMessage] =
    (messageStream: Stream[IO, StreamingMessage]) => messageStream.flatMap {
      case (csm: CommonStreamingMessage) => csm match {
        case (tweet: Tweet) => Stream.emit(tweet).observe(tweetSink).drain
        case _ => Stream.empty
      }
      case (_: SiteStreamingMessage) => Stream.empty
      case (_: UserStreamingMessage) => Stream.empty
        
    }

  // https://github.com/functional-streams-for-scala/fs2/blob/series/0.10/docs/migration-guide-0.10.md#performance
  
  val createTwitterStream: IO[Stream[IO, Tweet]] = for {
    twitterQueue <- createTwitterQueue
    val sink: Sink[IO, StreamingMessage] = streamingMessageEnqueue(twitterQueue.enqueue)
    val foo = streamingClient.FS2.sampleStatusesStream()(sink)
    val queueSizeStream: Stream[IO, Unit] = twitterQueue.size.discrete.flatMap { queueSize =>
      if(queueSize > 0 && queueSize % 5 == 0)
        Stream.eval(IO(println("queue size: "+queueSize)))
      else
        Stream.eval(IO(()))
    }
  } yield queueSizeStream.drain.mergeHaltR(twitterQueue.dequeue)

}

// object TwitterQueueExample {

//   import fs2._

//   // val printTwitter = for (
//   //   twitterStream <- TwitterQueue.createTwitterStream
//   //   val drained = twitterStream.map(_.text).take(64).through(text.utf8Encode).observe(io.stdout).drain.run
//   //   // val drained = twitterStream.map(_.text).through(text.utf8Encode).observe(io.stdout).drain
//   // ) yield drained  // TODO do this without yield

//   val printTwitter: IO[Unit] = TwitterQueue.createTwitterStream.flatMap { twitterStream =>
//     twitterStream
//       .map(tweet => tweet.user.map(_.name).getOrElse("nobody"))
//       .intersperse("\n")
//       // .through(text.lines)
//       .through(text.utf8Encode)
//       .observe(io.stdout)
//       .drain.run
//   }

//   def main(args: Array[String]): Unit = {
//     println("twitter queue example, with FS2")

//     printTwitter.unsafeRunSync()
//   }


// }
