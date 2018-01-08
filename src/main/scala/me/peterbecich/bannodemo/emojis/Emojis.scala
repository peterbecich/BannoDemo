package me.peterbecich.bannodemo.emojis

import io.circe._
import io.circe.Encoder
import io.circe.syntax._
import io.circe.literal._
import io.circe.generic.semiauto._

object Emojis {

  // case class Emoji(
  //   name: String,
  //   unified: String,
  //   non_qualified: String,
  //   docomo: String,
  //   au: String,
  //   softbank: String,
  //   google: String,
  //   image: String,
  //   sheet_x: Int,
  //   sheet_y: Int,
  //   short_name: String,
  //   short_names: List[String],
  //   text: String,
  //   texts: String,
  //   category: String,
  //   sort_order: Int,
  //   added_in: String,
  //   has_img_apple: Boolean,
  //   has_img_google: Boolean,
  //   has_img_twitter: Boolean,
  //   has_img_emojione: Boolean,
  //   has_img_facebook: Boolean,
  //   has_img_messenger: Boolean
  // )

  case class Emoji(
    // name: String,
    unified: String,
    // non_qualified: String,
    // docomo: String,
    // au: String,
    // softbank: String,
    // google: String,
    image: String,
    // sheet_x: String,
    // sheet_y: String,
    short_name: String,
    short_names: List[String],
    // text: String,
    // texts: String,
    category: String,
    // sort_order: String,
    added_in: String,
    // has_img_apple: String,
    // has_img_google: String,
    // has_img_twitter: String,
    // has_img_emojione: String,
    // has_img_facebook: String,
    // has_img_messenger: String
  )
  
  implicit val emojiDecoder: Decoder[Emoji] = deriveDecoder


  type EmojisCollection = List[Emoji]

  import scala.io.Source
  import io.circe.parser.decode

  // 
  lazy val emojis: Either[Error, EmojisCollection] = {
    val emojisPath = "/srv/emojis/emoji_pretty.json"
    val emojisText = Source.fromFile(emojisPath).getLines.mkString

    decode[EmojisCollection](emojisText)
  }


}

object EmojisExample extends App {
  import Emojis._

  import io.circe.parser.decode


  val emojisPath = "src/main/resources/emojis/emoji_pretty.json"

  import scala.io.Source

  // https://alvinalexander.com/scala/how-to-open-read-text-files-in-scala-cookbook-examples
  val emojisText = Source.fromFile(emojisPath).getLines.mkString

  println("characters in emojis json dump: "+emojisText.length)

  // https://circe.github.io/circe/codec.html

  // val decodedEmojis = emojisText.as[EmojisCollection]
  // unsafe!!!
  val eitherDecodedEmojis = decode[EmojisCollection](emojisText)

  eitherDecodedEmojis match {
    case Left(err) => println(err)
    case Right(decodedEmojis) => {
      println("some decoded emojis:")
      decodedEmojis.take(16).foreach(println(_))
    }
  }

  val mahjong = "\u1F004"

  val tweets = "🎶 👂 🤑 🎒 💛 😂 👏 🐼 📸 💕"

  println("tweets")
  println(tweets)

  println("mahjong")
  println(mahjong.toString)

  // https://stackoverflow.com/questions/24968645/how-to-get-unicode-of-smiley-in-scala
  val smiley = "\u263a"
  println(smiley)

  val smiley2 = "☺"
  println("another smiley")
  println(smiley2)

  println("smiley equality")
  println(smiley==smiley2)

  println("filing cabinet")

  val filingCabinet = "\ufe0f"
  println(filingCabinet)

  println("smiley - filing cabinet equality check")
  println(smiley==filingCabinet)

  

  // BannoDemo/src/main/scala/me/peterbecich/bannodemo/twitter/stats/



}
