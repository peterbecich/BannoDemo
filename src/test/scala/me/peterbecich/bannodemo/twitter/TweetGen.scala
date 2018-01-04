package me.peterbecich.bannodemo.twitter

import org.scalacheck._
import org.scalacheck.Arbitrary._
// import org.scalacheck.Properties
// import org.scalacheck.Prop.forAll

import org.scalatest._
import org.scalatest.prop._
import org.scalatest.Matchers._

import java.util.Date

import scala.collection.Map

import com.danielasfregola.twitter4s.entities.Tweet


/*
 https://www.scalacheck.org/
 https://www.scalacheck.org/files/scalacheck_2.11-1.13.4-api/index.html#org.scalacheck.Gen$

 http://www.scalatest.org/user_guide/writing_scalacheck_style_properties
 http://www.scalatest.org/user_guide/property_based_testing
 http://doc.scalatest.org/3.0.1/#org.scalatest.prop.GeneratorDrivenPropertyChecks
 */
object TweetGen {

  case class Foo(a: String, b: Int)

  val genFoo: Gen[Foo] = for {
    _a <- arbitrary[String]
    _b <- arbitrary[Int]
  } yield Foo(_a, _b)

  val tweetGen: Gen[Tweet] = for {
    _created_at <- arbitrary[Date]
    _favorite_count <- arbitrary[Int]
    _favorited <- arbitrary[Boolean]
    _id <- arbitrary[Long]
    _id_str = _id.toString()
    _is_quote_status <- arbitrary[Boolean]
    _possibly_sensitive <- arbitrary[Boolean]
    _scopes = Map.empty[String, Boolean]
    _retweet_count <- arbitrary[Long]
    _retweeted <- arbitrary[Boolean]
    _source <- arbitrary[String]
    // _text <- arbitrary[String]
    _text <- Gen.alphaStr.map( str => str.take(280) )
    _truncated <- arbitrary[Boolean]
    _withheld_copyright <- arbitrary[Boolean]
    _withheld_in_countries = Seq()
  } yield Tweet(Seq(), None, _created_at, None, None, None,
    _favorite_count, _favorited, None, None, _id, _id_str,
    None, None, None, None, None, _is_quote_status, None, None,
    _possibly_sensitive, None, None, None, _scopes, _retweet_count,
    _retweeted, None, _source, _text, _truncated, None, _withheld_copyright,
    _withheld_in_countries, None, None)


  implicit val tweetArbitrary: Arbitrary[Tweet] = Arbitrary(tweetGen)

}

