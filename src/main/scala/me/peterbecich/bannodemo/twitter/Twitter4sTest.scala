package me.peterbecich.bannodemo.twitter


import com.danielasfregola.twitter4s.TwitterStreamingClient
// import com.danielasfregola.twitter4s.http.clients.streaming.FS2._
import com.danielasfregola.twitter4s.fs2.http.clients.streaming.statuses.FS2._

object Twitter4sTest extends App {

  println("twitter4s class enrichment test")

  val streamingClient = TwitterStreamingClient()

  // streamingClient.sampleStatusesFS2

}

