package me.peterbecich.bannodemo.twitter

import org.scalacheck._
import org.scalacheck.Arbitrary._
// import org.scalacheck.Properties
// import org.scalacheck.Prop.forAll

import org.scalatest._
import org.scalatest.prop._
import org.scalatest.Matchers._

import java.util.Date
import java.time.LocalDateTime

import scala.collection.Map

import com.danielasfregola.twitter4s.entities.Tweet

/*
 https://gist.github.com/davidallsopp/60d7474a1fe8dc9b1f2d
 http://www.scalatest.org/user_guide/using_matchers

 Demonstrate ScalaTest's style
 */

class TweetSpec extends PropSpec with PropertyChecks with Matchers {
  import TweetGen._

  property("max Tweet length <= 280 characters") {
    // println("tweet sample")
    // println(tweetGen.sample)

    forAll { (tweet: Tweet) =>
      // println("tweet: "+tweet)
      tweet.text.length() should be <= 280
    }
  }

  property("generated Tweet timestamps later than time of beginning of test") {
    // val now = LocalDateTime.now()
    // println("test begins: "+TweetGen.testBeginDate)
    Thread.sleep(1000)
    forAll { (tweet: Tweet) =>
      // println("test begins: "+TweetGen.testBeginDate)
      // println("tweet time: "+tweet.created_at.toString())
      tweet.created_at.after(TweetGen.testBeginDate) should be (true)
    }
  }

  property("generated old Tweet timestamps earlier than time of beginning of test") {
    // val now = LocalDateTime.now()
    // println("test begins: "+TweetGen.testBeginDate)
    Thread.sleep(1000)
    forAll(oldTweetGen) { (tweet: Tweet) =>
      // println("test begins: "+TweetGen.testBeginDate)
      // println("tweet time: "+tweet.created_at.toString())
      tweet.created_at.after(TweetGen.testBeginDate) should be (false)
    }
  }
  

}


/*
 Demonstrate ScalaCheck's style
 */
object TweetSpecification extends Properties("Tweet") {
  import TweetGen._
  import org.scalacheck.Prop.forAll

  property("max length <= 280 characters") = forAll { (tweet: Tweet) =>
    // println("tweet: "+tweet)
    tweet.text.length() <= 280
  }

}

