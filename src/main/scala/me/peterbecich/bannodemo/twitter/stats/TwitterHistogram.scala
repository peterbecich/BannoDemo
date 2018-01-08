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

import scala.util.matching.Regex

import java.time.{LocalDateTime, ZoneOffset, Duration}
import java.time.temporal.ChronoUnit

import io.circe.generic.encoding.DerivedObjectEncoder

import me.peterbecich.bannodemo.twitter.TwitterStats.getTweetTime

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object RegexExample {

  /*
   https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html
   http://www.scala-lang.org/api/2.12.4/scala/util/matching/Regex.html
   */

  // https://stackoverflow.com/a/1919995/1007926
  val http: Regex = raw"http.*?com".r

  val tweet: String = "aeuahsouthasoue http://news.google.com aseudasoehud https://yahoo.com sahxksarodis"

  val tweets: List[String] = List(
    "hello http://www.google.com",
    "aeuahsouthasoue http://news.google.com",
    "asueaonutdasoeuh aoedusanhoudnsathoud aesutasoeunth",
    "https://nytimes.com",
    "asoeudasoeu https://www.twitter.com aodeusahoeuaoheu",
    "twitter asontuaontudaso facebook",
    "satheusaotheu https://en.wikipedia.org astudaonuedsaob"
  )

  // Regex => Set[K]

  // def makeKeys(tweet: Tweet, regex: Regex): Set[String] =


}




object TwitterHistogram {

  type Histogram = (IndexedSeq[String], TrieMap[String, Long])
  type HistogramSignal = Signal[IO, Histogram]

  private def makeHistogramSignal: IO[HistogramSignal] =
    Signal.apply((IndexedSeq.empty[String], TrieMap.empty[String, Long]))(IO.ioEffect, global)

  object JSON {
    import io.circe._
    import io.circe.Encoder
    import io.circe.syntax._
    import io.circe.literal._
    import io.circe.generic.semiauto._

    import me.peterbecich.bannodemo.JSON.Common._

    case class HistogramPayload(
      name: String,
      histogram: Map[String, Long]
    )

    // TODO
    // implicit val doe: DerivedObjectEncoder[HistogramPayload] = ???

    // TODO take cut-off as argument; include only top-N items in histogram payload

    implicit lazy val histogramPayloadEncoder: Encoder[HistogramPayload] =
      deriveEncoder[HistogramPayload]
    
    def makeHistogramPayload( _name: String, _hist: Map[String, Long] ):
        HistogramPayload =
      HistogramPayload(_name, _hist)

    def makeHistogramPayloadJson( _name: String, _hist: Map[String, Long] ):
        io.circe.Json =
      makeHistogramPayload(_name, _hist).asJson

  }

  // def makeTwitterHistogram
  //   ( _name: String,
  //     _predicate: Tweet => Option[String],
  //     _bins: Set[String] = HashSet.empty[String],
  //     _growBins: Boolean = true
  //   ): IO[TwitterHistogram] = for { 
  //   _histogramSignal <- makeHistogramSignal
  //   val bins = (new HashSet()) ++ _bins
  // } yield {
  //   new TwitterHistogram(_name, _histogramSignal, _predicate, bins) {}
  // }

  def makeTwitterHistogramRegex
    ( _name: String,
      _regex: Regex,
      _search: Function1[Tweet, String] = _.text,
      _bins: Set[String] = HashSet.empty,
      _growBins: Boolean = true
    ): IO[TwitterHistogram] = for { 
    _histogramSignal <- makeHistogramSignal
  } yield {
    new TwitterHistogram(_name, _regex, _histogramSignal, _search, _growBins) {}
  }
  
}

import TwitterHistogram._


abstract class TwitterHistogram
  (
    val name: String,
    val regex: Regex,
    val histogramSignal: HistogramSignal,
    val search: Function1[Tweet, String] = _.text,
    val growBins: Boolean = true
  ){


  private def getKeys(tweet: Tweet): Set[String] =
    regex.findAllIn(search(tweet)).toSet

  private def sortBins
    (bins: IndexedSeq[String], histogram: TrieMap[String, Long]): IndexedSeq[String] =
    bins.sortBy[Long]{ (k: String) => histogram.getOrElse(k, 0) }.reverse

  // take top from histogram
  val n = 32

  lazy val histogramPayloadStream: Stream[IO, JSON.HistogramPayload] =
    histogramSignal.discrete.map { case (bins, histogram) =>
      val topBins = bins.take(n)
      val topHistogram = histogram.filter { case (key, _) =>
        topBins.contains(key)
      }.toMap
      JSON.makeHistogramPayload(name, topHistogram)
    }

  import cats.instances.set.catsStdInstancesForSet

  lazy val commutativeIO: CommutativeApplicative[IO] =
    cats.CommutativeApplicative.apply[IO](commutativeIO)

  private def incrementKey(tweet: Tweet): IO[Unit] =
    UnorderedTraverse[Set].unorderedTraverse(getKeys(tweet)){ key =>
      histogramSignal.modify { case (bins, histogram) =>
        if(bins.contains(key)) {
          val count: Long = histogram.getOrElse(key, 0)
          val _histogram: TrieMap[String, Long] = histogram += ((key, count+1))
          val _bins = sortBins(bins, _histogram)
          (_bins, _histogram)
        } else if (bins.contains(key) == false && growBins == true) {
          val _histogram: TrieMap[String, Long] = histogram += ((key, 1))
          val _bins = sortBins(key +: bins, _histogram)
          (_bins, _histogram)
        } else {
          (bins, histogram)
        }
      }.map(_ => (()))
    }(commutativeIO).map(_ => (()))

  private val incrementKeyPipe: Pipe[IO, Tweet, Tweet] =
    (tweetInput: Stream[IO, Tweet]) =>
    tweetInput.flatMap { tweet =>
      Stream.eval(incrementKey(tweet)).flatMap { _ =>
        Stream.emit(tweet)
      }
    }

  val histogramPipe: Pipe[IO, Tweet, Tweet] = incrementKeyPipe

}
