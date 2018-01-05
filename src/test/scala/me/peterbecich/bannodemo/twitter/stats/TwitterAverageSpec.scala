// package me.peterbecich.bannodemo.twitter.stats

// import cats._
// import cats.effect.{IO, Sync}
// import com.danielasfregola.twitter4s.entities.Tweet
// import fs2._
// import fs2.async.mutable.Queue
// import java.util.Date
// import org.scalacheck.Arbitrary._
// import org.scalacheck._
// import org.scalatest.Matchers._
// import org.scalatest._
// import org.scalatest.prop._
// import scala.collection.Map
// import scala.concurrent.ExecutionContext.Implicits.global

// import me.peterbecich.bannodemo.twitter.TweetGen._
// import me.peterbecich.bannodemo.twitter.TwitterSourceGen._

// import TwitterAverage._

// class TwitterAverageSpec extends PropSpec with PropertyChecks with Matchers {

//   implicit override val generatorDrivenConfig =
//     PropertyCheckConfig(minSize = 100, maxSize = 1500)

//   property("dummy test") {
//     forAll { (tweets: Stream[IO, Tweet]) =>
//       val makeTweetAverage: IO[TwitterAverage] = TwitterAverage.makeAverage("TweetAverage", (_) => true)

//       val triemap: IO[TimeTable] =
//         makeTweetAverage.flatMap { tweetAverage =>
//           tweets.through(tweetAverage.averagePipe).drain.run.flatMap { _ =>
//             tweetAverage.timeTableSignal.get
//           }
//         }

//       triemap.unsafeRunSync.size should be >= 0

//     }
//   }

//   property("Tweets pass through averaging pipeline") {
//     forAll { (tweets: Stream[IO, Tweet]) =>
//       val makeTweetAverage: IO[TwitterAverage] = TwitterAverage.makeAverage("TweetAverage", (_) => true)

//       val getCount: IO[Int] =
//         makeTweetAverage.flatMap { tweetAverage =>
//           tweets.through(tweetAverage.averagePipe).map(_ => 1).runFold(0) { (s, _) => s+1 }
//         }

//       val count = getCount.unsafeRunSync()
//       // println("count: "+count)
//       count should be > 0

//     }
//   }


//   property("Time Table is not empty after Tweets pass through TwitterAverage; use _makeTweetAverage") {
//     forAll { (tweets: Stream[IO, Tweet]) =>
//       val makeTweetAverage: IO[(TwitterAverage, TimeTableSignal)] =
//         TwitterAverage._makeAverage("TweetAverage", (_) => true)

//       val getTriemap: IO[TimeTable] =
//         makeTweetAverage.flatMap { case (tweetAverage, timeTableSignal) =>
//           tweets.through(tweetAverage.averagePipe).drain.run.flatMap { _ =>
//             timeTableSignal.get
//           }
//         }

//       val triemap = getTriemap.unsafeRunSync
//       // println("time table size: "+triemap.size)
//       triemap.size should be > 0

//     }
//   }


//   property("Time Table is not empty after Tweets pass through TwitterAverage; use makeTweetAverage") {
//     forAll { (tweets: Stream[IO, Tweet]) =>
//       val makeTweetAverage: IO[TwitterAverage] =
//         TwitterAverage.makeAverage("TweetAverage", (_) => true)

//       val getTriemap: IO[TimeTable] =
//         makeTweetAverage.flatMap { tweetAverage =>
//           tweets.through(tweetAverage.averagePipe).drain.run.flatMap { _ =>
//             tweetAverage.timeTableSignal.get
//           }
//         }

//       val triemap = getTriemap.unsafeRunSync
//       // println("time table size: "+triemap.size)
//       triemap.size should be > 0

//     }
//   }

//   property("Tweets older than one hour are removed from TrieMap") {
//     forAll(oldTwitterStreamGen) { (tweets: Stream[IO, Tweet]) =>
//       val makeTweetAverage: IO[TwitterAverage] =
//         TwitterAverage.makeAverage("TweetAverage", (_) => true)

//       val getTriemap: IO[TimeTable] =
//         makeTweetAverage.flatMap { tweetAverage =>
//           tweets.through(tweetAverage.averagePipe).drain.run.flatMap { _ =>
//             tweetAverage.timeTableSignal.get
//           }
//         }

//       val triemap = getTriemap.unsafeRunSync
//       // println("time table size: "+triemap.size)
//       triemap.size should be <= 10

//     }
//   }

//   // property("Count of Tweets in Time Table is greater than 0") {
//   //   forAll { (tweets: Stream[IO, Tweet]) =>
//   //     val makeTweetAverage: IO[TwitterAverage] =
//   //       TwitterAverage.makeAverage("TweetAverage", (_) => true)

//   //     val getHour: IO[(Long, Long)] =
//   //       makeTweetAverage.flatMap { tweetAverage =>
//   //         tweets.through(tweetAverage.averagePipe).drain.run.flatMap { _ =>
//   //           tweetAverage.hourSumSignal.get
//   //         }
//   //       }

//   //     val (sum, count) = getHour.unsafeRunSync
//   //     // println("sum: "+sum+" count: "+count)
//   //     sum.toInt should be > 0

//   //   }
//   // }
  
  
//   property("TwitterAverage produces at least one AveragePayload") {
//     forAll { (tweets: Stream[IO, Tweet]) =>
//       val makeTweetAverage: IO[TwitterAverage] =
//         TwitterAverage.makeAverage("TweetAverage", (_) => true)

//       val getAveragePayloads: IO[Stream[IO, TwitterAverage.JSON.AveragePayload]] =
//         makeTweetAverage.flatMap { tweetAverage =>
//           tweets.through(tweetAverage.averagePipe).drain.run.map { _ =>
//             tweetAverage.averagePayloadStream
//           }
//         }

//       val getPayloadCount: IO[Int] = getAveragePayloads.flatMap { payloadStream =>
//         payloadStream.map { _ => 1}.take(1).runFold(0){ (s, _) => s + 1 }
//       }

//       val payloadCount = getPayloadCount.unsafeRunSync()

//       // println("average payload count: "+payloadCount)

//       payloadCount should be > 0
//     }
//   }

//   property("TwitterAverage produces more than one AveragePayload") {
//     forAll { (tweets: Stream[IO, Tweet]) =>
//       val makeTweetAverage: IO[TwitterAverage] =
//         TwitterAverage.makeAverage("TweetAverage", (_) => true)

//       def loadTweets(ave: TwitterAverage): Stream[IO, Unit] =
//         tweets.through(ave.averagePipe).drain

//       val getPayloadStream: IO[Stream[IO, TwitterAverage.JSON.AveragePayload]] =
//         makeTweetAverage.map { ave =>
//           ave.averagePayloadStream.concurrently(loadTweets(ave))
//         }

//       val getPayloadCount: IO[Int] =
//         getPayloadStream.flatMap { payloadStream =>
//           payloadStream.map { _ => 1 }.take(5).runFold(0){ (s, _) => s + 1 }
//         }

//       val payloadCount = getPayloadCount.unsafeRunSync()

//       // println("Twitter average should produce more than one AveragePayload")
//       // println("average payload count: "+payloadCount)

//       payloadCount should be > 1
//     }
//   }

//   property("TwitterAverage produces at least 1 AveragePayload, with slow Tweet stream") {
//     forAll(slowTwitterStreamGen) { (tweets: Stream[IO, Tweet]) =>
//       val makeTweetAverage: IO[TwitterAverage] =
//         TwitterAverage.makeAverage("TweetAverage", (_) => true)

//       def loadTweets(ave: TwitterAverage): Stream[IO, Unit] =
//         tweets.through(ave.averagePipe).drain

//       val getPayloadStream: IO[Stream[IO, TwitterAverage.JSON.AveragePayload]] =
//         makeTweetAverage.map { ave =>
//           slowStream(ave.averagePayloadStream.concurrently(loadTweets(ave)))
//         }

//       val getPayloadCount: IO[Int] =
//         getPayloadStream.flatMap { payloadStream =>
//           payloadStream.map { _ => 1 }.take(5).runFold(0){ (s, _) => s + 1 }
//         }

//       val payloadCount = getPayloadCount.unsafeRunSync()

//       // println("Twitter average should produce more than one AveragePayload; slow Tweet stream")
//       // println("average payload count from slow stream: "+payloadCount)

//       payloadCount should be > 0
//     }
//   }
  
  

// }






