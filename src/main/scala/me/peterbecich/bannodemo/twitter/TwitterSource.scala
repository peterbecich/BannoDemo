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
      case (ssm: SiteStreamingMessage) => {
        println("SiteStreamingMessage: "+ssm)
        Stream.empty
      }
      case (usm: UserStreamingMessage) => {
        println("UserStreamingMessage: "+usm)
        Stream.empty
      }
        
    }

  // https://github.com/functional-streams-for-scala/fs2/blob/series/0.10/docs/migration-guide-0.10.md#performance

  private lazy val schedulerStream: Stream[IO, Scheduler] = Scheduler.apply[IO](2)

  import com.danielasfregola.twitter4s.http.clients.streaming.TwitterStream
  import scala.concurrent.Future
  import scala.concurrent.duration._
  import scala.util.Try
  
  val createTwitterStream: IO[Stream[IO, Tweet]] = createTwitterQueue.flatMap { twitterQueue =>
    val sink: Sink[IO, StreamingMessage] = streamingMessageEnqueue(twitterQueue.enqueue)
    val fTwitterStream: Future[TwitterStream] =
      streamingClient.FS2.sampleStatusesStream()(sink)

    val watchQueueSize: Stream[IO, Unit] = schedulerStream.flatMap { scheduler =>
      scheduler.fixedRate(30.second)(IO.ioEffect, global).flatMap { _ =>
        Stream.eval(twitterQueue.size.get).map { queueSize =>
          "Twitter Source queue size: " + queueSize + "\n"
        }
          .intersperse("\n")
          .through(fs2.text.utf8Encode)
          .observe(fs2.io.stdout)
          .drain
      }
    }

    IO(twitterQueue.dequeue.buffer(16).concurrently(watchQueueSize.drain))

  } 

}

