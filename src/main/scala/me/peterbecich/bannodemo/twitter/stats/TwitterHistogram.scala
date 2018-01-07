package me.peterbecich.bannodemo.twitter.stats

import cats._
import cats.implicits._
import cats.syntax.all._
import cats.effect.{IO, Sync}

import fs2.{Stream, Pipe, Scheduler}
import fs2.async.mutable.Signal
import fs2.async.immutable.{Signal => ISignal}

import com.danielasfregola.twitter4s.entities.Tweet

import scala.collection.concurrent.TrieMap
import scala.collection.immutable.Map
import scala.collection.immutable.HashSet
import scala.collection.immutable.IndexedSeq

import java.time.{LocalDateTime, ZoneOffset, Duration}
import java.time.temporal.ChronoUnit

import io.circe.generic.encoding.DerivedObjectEncoder

import me.peterbecich.bannodemo.twitter.TwitterStats.getTweetTime

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


object TwitterHistogram {

  type Histogram[K] = (IndexedSeq[K], TrieMap[K, Long])
  type HistogramSignal[K] = Signal[IO, Histogram[K]]

  private def makeHistogramSignal[K]: IO[HistogramSignal[K]] =
    Signal.apply((IndexedSeq.empty[K], TrieMap.empty[K, Long]))(IO.ioEffect, global)

  def makeTwitterHistogram[K : DerivedObjectEncoder]
    ( _name: String,
      _predicate: Tweet => Option[K],
      _bins: Set[K] = HashSet.empty[K],
      _growBins: Boolean = false
    ): IO[TwitterHistogram[K]] = for { 
    _histogramSignal <- makeHistogramSignal[K]
    val bins = (new HashSet()) ++ _bins
  } yield {
    new TwitterHistogram[K](_name, _histogramSignal, _predicate, bins) {}
  }
  
}

import TwitterHistogram._

abstract class TwitterHistogram[K : io.circe.Encoder]
  (
    val name: String,
    val histogramSignal: HistogramSignal[K],
    val predicate: Tweet => Option[K],
    val bins: HashSet[K] = HashSet.empty[K],
    val growBins: Boolean = true
  )
  (implicit val __kEncoder: DerivedObjectEncoder[K]) {

  // lazy implicit val kEncoder: DerivedObjectEncoder[K] = _kEncoder

  import TwitterHistogram._

  object JSON {
    import io.circe._
    import io.circe.Encoder
    import io.circe.syntax._
    import io.circe.literal._
    import io.circe.generic.semiauto._
    // import io.circe.generic.auto._


    import me.peterbecich.bannodemo.JSON.Common._

    case class HistogramPayload(
      name: String,
      histogram: Map[K, Long]
    )

    // TODO
    implicit val doe: DerivedObjectEncoder[HistogramPayload] = ???

    implicit lazy val histogramPayloadEncoder: Encoder[HistogramPayload] =
      deriveEncoder[HistogramPayload]
    
    def makeHistogramPayload( _name: String, _hist: Map[K, Long] ):
        HistogramPayload =
      HistogramPayload(_name, _hist)

    def makeHistogramPayloadJson( _name: String, _hist: Map[K, Long] ):
        io.circe.Json =
      makeHistogramPayload(_name, _hist).asJson

  }
  

  private def sortBins(bins: IndexedSeq[K], histogram: TrieMap[K, Long]): IndexedSeq[K] =
    bins.sortBy[Long]{ (k: K) => histogram.getOrElse(k, 0) }.reverse

  private def incrementKey(tweet: Tweet): IO[Unit] = predicate(tweet) match {
    case Some(k) if bins.contains(k) =>
      histogramSignal.modify { case (bins, histogram) =>
        val count: Long = histogram.getOrElse(k, 0)
        val _histogram: TrieMap[K, Long] = histogram += ((k, count+1))
        val _bins = sortBins(bins, _histogram)
        (_bins, _histogram)
      }.map(_ => ())
    case Some(k) if growBins == false => IO(())
    case Some(k) if growBins == true =>
      histogramSignal.modify { case (bins, histogram) =>
        val count: Long = histogram.getOrElse(k, 0)
        val _histogram: TrieMap[K, Long] = histogram += ((k, count+1))
        val _bins = sortBins(k +: bins, _histogram)
        (_bins, _histogram)
      }.map(_ => ())
    case None => IO(())
  }

  private val incrementKeyPipe: Pipe[IO, Tweet, Tweet] =
    (tweetInput: Stream[IO, Tweet]) =>
    tweetInput.flatMap { tweet =>
      Stream.eval(incrementKey(tweet)).flatMap { _ =>
        Stream.emit(tweet)
      }
    }

  val histogramPipe: Pipe[IO, Tweet, Tweet] = incrementKeyPipe

}
