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
import fs2.{io, text, Stream}
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

  val createTwitterQueue: IO[Queue[IO, Tweet]] = Queue.unbounded[IO, Tweet]

  // TODO replace this! enqueue safely
  def unsafeTweetEnqueue(queue: Queue[IO, Tweet]):
      PartialFunction[StreamingMessage, Unit] = {
    case tweet: Tweet =>
      queue.enqueue1(tweet).unsafeRunAsync((e: Either[Throwable, Unit]) => ())
  }

  val createTwitterStream: IO[Stream[IO, Tweet]] = for {
    twitterQueue <- createTwitterQueue
    val foo = streamingClient.sampleStatuses()(unsafeTweetEnqueue(twitterQueue))

  } yield twitterQueue.dequeue


}

object TwitterQueueExample {

  // val printTwitter = for (
  //   twitterStream <- TwitterQueue.createTwitterStream
  //   val drained = twitterStream.map(_.text).take(64).through(text.utf8Encode).observe(io.stdout).drain.run
  //   // val drained = twitterStream.map(_.text).through(text.utf8Encode).observe(io.stdout).drain
  // ) yield drained  // TODO do this without yield

  val printTwitter: IO[Unit] = TwitterQueue.createTwitterStream.flatMap { twitterStream =>
    twitterStream
      .map(t => t.user.map(_.name).getOrElse("nobody"))
      .intersperse("\n")
      // .through(text.lines)
      .through(text.utf8Encode)
      .observe(io.stdout)
      .drain.run
  }

  def main(args: Array[String]): Unit = {
    println("twitter queue example, with FS2")

    printTwitter.unsafeRunSync()
  }


}
