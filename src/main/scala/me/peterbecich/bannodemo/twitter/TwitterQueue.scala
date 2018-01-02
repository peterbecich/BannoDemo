package me.peterbecich.bannodemo.twitter

import java.util.Scanner;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import com.danielasfregola.twitter4s.TwitterStreamingClient
import com.danielasfregola.twitter4s.entities.Tweet
import com.danielasfregola.twitter4s.entities.streaming.StreamingMessage

import cats._
import cats.syntax.all._
import cats.effect.{IO, Sync}
// import fs2.{io, text, Stream, Chunk, Segment}
// import fs2.async.mutable.Queue
import fs2._
import fs2.async.mutable.Queue

import scala.concurrent.ExecutionContext.Implicits.global

/*
 Twitter4s
 https://github.com/DanielaSfregola/twitter4s

 Functional Streams for Scala
 https://github.com/functional-streams-for-scala/fs2
 */

object TwitterQueue {


  val streamingClient = TwitterStreamingClient()

  /*
   https://oss.sonatype.org/service/local/repositories/releases/archive/co/fs2/fs2-core_2.12/0.9.6/fs2-core_2.12-0.9.6-javadoc.jar/!/fs2/async/mutable/Queue.html

   https://oss.sonatype.org/service/local/repositories/releases/archive/org/typelevel/cats-effect_2.12/0.5/cats-effect_2.12-0.5-javadoc.jar/!/cats/effect/IO.html

   https://oss.sonatype.org/service/local/repositories/releases/archive/co/fs2/fs2-io_2.12/0.9.6/fs2-io_2.12-0.9.6-javadoc.jar/!/fs2/io/index.html
   */

  //val createTwitterQueue: IO[Queue[IO, Tweet]] = Queue.unbounded[IO, Tweet]
  val createTwitterQueue: IO[Queue[IO, Tweet]] = Queue.circularBuffer(1024)

  // val printQueueSize: IO[Stream[IO, Unit]] = createTwitterQueue
  //   createTwitterQueue.size.discrete.flatMap { queueSize =>
  //     if 

  // TODO replace this! enqueue safely
  // TODO enqueue in chunks, here
  // chunks used poorly, right now
  // https://youtu.be/HM0mOu5o2uA
  def unsafeTweetEnqueue(queue: Queue[IO, Tweet]):
      PartialFunction[StreamingMessage, Unit] = {
    case tweet: Tweet =>
      queue.enqueue1(tweet).unsafeRunAsync {
        case Left(throwable) => println("enqueue error: "+throwable.toString)
        case Right(_) => ()
      }
  }

  import com.danielasfregola.twitter4s.entities.streaming.{StreamingMessage, CommonStreamingMessage, UserStreamingMessage, SiteStreamingMessage}
  import com.danielasfregola.twitter4s.entities.Tweet

  def streamingMessageEnqueue(tweetSink: Sink[IO, Tweet]): Sink[IO, StreamingMessage] =
    (messageStream: Stream[IO, StreamingMessage]) => messageStream.flatMap {
      case (csm: CommonStreamingMessage) => csm match {
        case (tweet: Tweet) => Stream.emit(tweet).observe(tweetSink).drain
        case _ => Stream.empty
      }
      case (_: SiteStreamingMessage) => Stream.empty
      case (_: UserStreamingMessage) => Stream.empty
        
    }
  

  // https://oss.sonatype.org/service/local/repositories/releases/archive/co/fs2/fs2-core_2.12/0.10.0-M8/fs2-core_2.12-0.10.0-M8-javadoc.jar/!/fs2/async/mutable/Queue.html
  // https://github.com/functional-streams-for-scala/fs2/blob/series/0.10/docs/migration-guide-0.10.md#performance
  
  val createTwitterStream: IO[Stream[IO, Tweet]] = for {
    twitterQueue <- createTwitterQueue
    //val foo = streamingClient.sampleStatuses()(unsafeTweetEnqueue(twitterQueue))
    val foo = streamingClient.FS2.sampleStatusesStream()(streamingMessageEnqueue(twitterQueue.enqueue))
    val queueSizeStream: Stream[IO, Unit] = twitterQueue.size.discrete.flatMap { queueSize =>
      if(queueSize > 0 && queueSize % 5 == 0)
        Stream.eval(IO(println("queue size: "+queueSize)))
      else
        Stream.eval(IO(()))
    }
  } yield queueSizeStream.drain.mergeHaltR(twitterQueue.dequeue)

//queueSizeStream.mergeHaltL(twitterQueue.dequeue)
//queueSizeStream.flatMap{ _ => twitterQueue.dequeue }  //Stream.eval(twitterQueue.dequeueBatch1(32)).map { chunk => Segment.chunk(chunk) }


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
