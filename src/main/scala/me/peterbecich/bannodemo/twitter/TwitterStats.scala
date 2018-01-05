package me.peterbecich.bannodemo.twitter

import cats._
import cats.effect._
import cats.implicits._
import cats.syntax.all._
import com.danielasfregola.twitter4s.entities.Tweet
import fs2.Stream
import io.circe.Encoder
import io.circe._
import io.circe.generic.semiauto._
import io.circe.literal._
import io.circe.syntax._
import java.time.{LocalDateTime, ZonedDateTime}
import me.peterbecich.bannodemo.JSON.Common._
import org.http4s.circe._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import me.peterbecich.bannodemo.twitter.stats.TwitterAccumulators
import me.peterbecich.bannodemo.twitter.stats.TwitterAccumulators._
import me.peterbecich.bannodemo.twitter.stats.TwitterAverages

import me.peterbecich.bannodemo.HelloWorldServer.serverStart

/*
 https://docs.oracle.com/javase/8/docs/api/java/time/ZonedDateTime.html

 http://http4s.org/v0.18/json/
 http://http4s.org/v0.18/entity/
 */

object TwitterStats {

  import cats._
  import cats.implicits._

  object JSON {
    import io.circe._
    import io.circe.Encoder
    import io.circe.syntax._
    import io.circe.literal._
    import io.circe.generic.semiauto._

    import stats.TwitterAverages.JSON._
    import stats.TwitterAccumulators.JSON._

    val serverStart = ZonedDateTime.now()

    case class StatsPayload (
      serverStartTimestamp: ZonedDateTime,
      statsTimestamp: ZonedDateTime,
      averages: AveragesPayload,
      accumulators: AccumulatorsPayload
    )

    implicit val statsPayloadEncoder: Encoder[StatsPayload] = deriveEncoder

    private def statsPayloadStream(
      averagesPayloadStream: Stream[IO, stats.TwitterAverages.JSON.AveragesPayload],
      accumulatorsPayloadStream: Stream[IO, stats.TwitterAccumulators.JSON.AccumulatorsPayload]
    ): Stream[IO, StatsPayload] =
      averagesPayloadStream
        .zip(accumulatorsPayloadStream)
        .zip(Stream.repeatEval(IO(ZonedDateTime.now())))
        .map { case ((averages, accumulators), now) =>
          StatsPayload(serverStart, now, averages, accumulators)
        }

    def statsPayloadJsonStream(
      averagesPayloadStream: Stream[IO, stats.TwitterAverages.JSON.AveragesPayload],
      accumulatorsPayloadStream: Stream[IO, stats.TwitterAccumulators.JSON.AccumulatorsPayload]
    ): Stream[IO, Json] =
      statsPayloadStream(averagesPayloadStream, accumulatorsPayloadStream)
        .map(_.asJson)

  }

  lazy val collectStats: IO[Stream[IO, Json]] = {
    for {
      _ <- IO(println("acquire Twitter stream"))
      twitterStream <- TwitterSource.createTwitterStream
      _ <- IO(println("make accumulators pipeline"))
      accumulatorTup <- TwitterAccumulators.makeTwitterAccumulator
      (accumulatorPipe, accumulatorsPayloadStream) = accumulatorTup
      _ <- IO(println("make averages pipeline"))
      averagesTup <- TwitterAverages.makeTwitterAverages
      (averagePipe, averagesPayloadStream) = averagesTup
      _ <- IO(println("begin"))
    } yield {
      twitterStream
        .through(accumulatorPipe)
        .through(averagePipe)
        .drain
        .run
        .runAsync { case _ => IO(()) }
        .flatMap { _ => IO(JSON.statsPayloadJsonStream(averagesPayloadStream, accumulatorsPayloadStream)) }
    }
  }.flatten

  // .runAsync { case _ => IO(()) }.flatMap { _ =>
  //   IO(averagesPayloadStream)
  // }

  // http://www.java67.com/2016/03/how-to-convert-date-to-localdatetime-in-java8-example.html
  // TODO don't use server's time zone for all tweet timestamps
  implicit def dateToLocalDateTime(date: java.util.Date): LocalDateTime =
    LocalDateTime.ofInstant(date.toInstant(), serverStart.getZone())

  def getTweetTime(tweet: Tweet): LocalDateTime = dateToLocalDateTime(tweet.created_at)


}
 
