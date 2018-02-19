package me.peterbecich.bannodemo.twitter.stats

import cats._
import cats.effect.{IO, Sync}
import cats.implicits._
import cats.syntax.all._
import com.danielasfregola.twitter4s.entities.Tweet
import fs2.async.immutable.{Signal => ISignal}
import fs2.async.mutable.Signal
import fs2.{Stream, Pipe, Scheduler}
import io.circe.generic.encoding.DerivedObjectEncoder
import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, ZoneOffset, Duration}
import me.peterbecich.bannodemo.twitter.TwitterStats.getTweetTime
import scala.collection.concurrent.TrieMap
import scala.collection.immutable.HashSet
import scala.collection.immutable.IndexedSeq
import scala.collection.immutable.Map
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.matching.Regex

object RegexExample {

  /*
   https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html
   http://www.scala-lang.org/api/2.12.4/scala/util/matching/Regex.html
   */

  // https://stackoverflow.com/a/1919995/1007926
  val http: Regex = raw"http.*?com".r

  val short: String = """https://t.co/SRgFJLuoTU"""

  val tweet: String = """aeuahsouthasoue http://news.google.com/foo aseudasoehud https://yahoo.com sahxksarodis"""

  val tweets: List[String] = List(
    "hello http://www.google.com",
    "aeuahsouthasoue http://news.google.com",
    "asueaonutdasoeuh aoedusanhoudnsathoud aesutasoeunth",
    "https://nytimes.com",
    "asoeudasoeu https://www.twitter.com aodeusahoeuaoheu",
    "twitter asontuaontudaso facebook",
    "satheusaotheu https://en.wikipedia.org astudaonuedsaob"
  )

  val hashtag: Regex = raw"""\#.+\s?""".r

  val hashtags: String = """sauehsanotuh #foo #bar asoeuthaoseuh #baz aseutha"""

}




object TwitterHistogram {

  type Histogram = (IndexedSeq[String], TrieMap[String, Long])
  type HistogramSignal = Signal[IO, Histogram]

  private def makeHistogramSignal
    (bins: IndexedSeq[String] = IndexedSeq.empty[String]):
      IO[HistogramSignal] =
    Signal.apply((bins, TrieMap.empty[String, Long]))(IO.ioEffect, global)

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

  def makeTwitterHistogram
    ( _name: String,
      _getKeys: Function1[Tweet, Seq[String]],
      _bins: IndexedSeq[String] = IndexedSeq.empty[String],
      _growBins: Boolean = true
    ): IO[TwitterHistogram] = for { 
    _histogramSignal <- makeHistogramSignal(_bins)
  } yield {
    new PredicateTwitterHistogram(_name, _histogramSignal, _getKeys) {}
  }

  def makeTwitterHistogramRegex
    ( _name: String,
      _regex: Regex,
      _search: Function1[Tweet, String] = _.text,
      _bins: IndexedSeq[String] = IndexedSeq(),
      _growBins: Boolean = true
    ): IO[TwitterHistogram] = for { 
    _histogramSignal <- makeHistogramSignal(_bins)
  } yield {
    RegexTwitterHistogram(_name, _regex, _histogramSignal, _search, _growBins)
  }
  
}

import TwitterHistogram._

trait TwitterHistogram {
  val name: String
  val histogramSignal: HistogramSignal
  val growBins: Boolean

  // take top from histogram
  val n = 32

  lazy val histogramPayloadStream: Stream[IO, JSON.HistogramPayload] =
    histogramSignal.discrete.map { case (bins, histogram) =>
      val topBins = bins.take(n).sorted.reverse
      val topHistogram = histogram.filter { case (key, _) =>
        topBins.contains(key)
      }.toMap
      JSON.makeHistogramPayload(name, topHistogram)
    }

  def incrementKey(tweet: Tweet): IO[Unit]

  private val incrementKeyPipe: Pipe[IO, Tweet, Tweet] =
    (tweetInput: Stream[IO, Tweet]) =>
  tweetInput.flatMap { tweet =>
    Stream.eval(incrementKey(tweet)).flatMap { _ =>
      Stream.emit(tweet)
    }
  }

  val histogramPipe: Pipe[IO, Tweet, Tweet] = incrementKeyPipe

}


case class PredicateTwitterHistogram(
    name: String,
    histogramSignal: HistogramSignal,
    getKeys: Function1[Tweet, Seq[String]],
    growBins: Boolean = true
  ) extends TwitterHistogram {

  private def sortBins
    (bins: IndexedSeq[String], histogram: TrieMap[String, Long]): IndexedSeq[String] =
    bins.sortBy[Long]{ (k: String) => histogram.getOrElse(k, 0) }.reverse

  def incrementKey(tweet: Tweet): IO[Unit] =
    Traverse[List].traverse_(getKeys(tweet).toList){ key =>
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
      }
    }

}


case class RegexTwitterHistogram(
    name: String,
    regex: Regex,
    histogramSignal: HistogramSignal,
    search: Function1[Tweet, String] = _.text,
    growBins: Boolean = true
  ) extends TwitterHistogram {


  private def getKeys(tweet: Tweet): IndexedSeq[String] =
    regex.findAllIn(search(tweet)).toIndexedSeq

  private def sortBins
    (bins: IndexedSeq[String], histogram: TrieMap[String, Long]): IndexedSeq[String] =
    bins.sortBy[Long]{ (k: String) => histogram.getOrElse(k, 0) }.reverse

  def incrementKey(tweet: Tweet): IO[Unit] =
    Traverse[List].traverse_(getKeys(tweet).toList){ key =>
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
      }
    }

}
