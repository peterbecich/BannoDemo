package me.peterbecich.bannodemo

import java.util.Scanner;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import com.danielasfregola.twitter4s.TwitterStreamingClient
import com.danielasfregola.twitter4s.entities.Tweet
import com.danielasfregola.twitter4s.entities.streaming.StreamingMessage



/*
 https://github.com/scribejava/scribejava/wiki/getting-started
 https://github.com/scribejava/scribejava/blob/master/scribejava-apis/src/test/java/com/github/scribejava/apis/examples/TwitterExample.java

 Twitter4s
 https://github.com/DanielaSfregola/twitter4s
 */

object OAuthSample extends App {

  println("OAuth Sample")
  
  val streamingClient = TwitterStreamingClient()

  def printTweetText: PartialFunction[StreamingMessage, Unit] = {
    case tweet: Tweet => println(tweet.text)
  }

  streamingClient.sampleStatuses(stall_warnings = true)(printTweetText)

}
