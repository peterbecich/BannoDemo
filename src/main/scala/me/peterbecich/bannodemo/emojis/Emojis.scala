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
  ) {

    // https://stackoverflow.com/questions/10763730/hex-string-to-int-short-and-long-in-scala
    def getEmojiInt(unified: String): Option[Int] =
      scala.util.Try(Integer.parseInt(unified, 16)).toOption

    lazy val emojiInt: Option[Int] = getEmojiInt(unified)
  }

  
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
      decodedEmojis.slice(512,560).foreach(emoji => println(emoji.unified + "  " + emoji.emojiInt.map(_.toHexString)))
    }
  }

  val mahjong = '\u1F00'.toString

  println("mahjong:")
  println(mahjong)

  val tweets = "🎶 👂 🤑 🎒 💛 😂 👏 🐼 📸 💕"

  println(tweets)

  // https://stackoverflow.com/questions/236097/finding-the-unicode-codepoint-of-a-character-in-gnu-emacs
  val music = "aaa🎶zzz"
  val music2 = "🎶"
  
  // code point in charset: 0x1F3B6



  // {
  //     "name": "MULTIPLE MUSICAL NOTES",
  //     "unified": "1F3B6",
  //     "non_qualified": null,
  //     "docomo": "E6FF",
  //     "au": "E505",
  //     "softbank": "E326",
  //     "google": "FE814",
  //     "image": "1f3b6.png",
  //     "sheet_x": 9,
  //     "sheet_y": 16,
  //     "short_name": "notes",
  //     "short_names": [
  //         "notes"
  //     ],
  
  println("musical note:")
  println(music)
  
  // val musicChars = music.map(_.toInt.toHexString)
  // println("music chars:")
  // println(musicChars)

  // val musicChar = 

  val example = "1f3ec"
  println("example")
  println(example)
  println("\\u"+example)

  // https://docs.oracle.com/javase/8/docs/api/?java/lang/Integer.html
  // https://docs.oracle.com/javase/8/docs/api/java/lang/Integer.html#valueOf-java.lang.String-int-

  val example2 = "0x1f3ec"
  // val example2 = "0xfffff"
  println(example2)

  // val exampleInt: Integer = Integer.valueOf(example2, 4)
  // val exampleInt: Integer = Integer.getInteger(example2, 4)
  val exampleInt: Integer = Integer.decode(example2)
  println(exampleInt)
  println("example int")
  println(exampleInt.toInt.toHexString)

  // println("music char:")
  // println(musicChar.toString)

  // println("music hex: ")
  // val musicHex = musicChar.toHexString
  // println(musicHex)

  println("a int")
  // val aInt = Integer.parseInt("a", 16)
  // println(aInt)
  println('a'.toInt.toHexString)

  // https://stackoverflow.com/questions/28738342/convert-special-characters-to-unicode-escape-characters-scala

  println("music int")
  // val musicInt = Integer.parseInt(music2, 8)
  // val musicInt = Integer.toHexString(music2 | 0x10000).subString(1)

  // def mask(c: Char): Char = c | 0x10000
  // val musicInt = Integer.toHexString(music2.map(mask).subString(1))
  // println(musicInt)

  // println(musicInt.toHexString)


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
