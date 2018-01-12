package me.peterbecich.bannodemo.emojis

import cats._
import cats.implicits._
import cats.syntax.all._

import io.circe._
import io.circe.Encoder
import io.circe.syntax._
import io.circe.literal._
import io.circe.generic.semiauto._

object Emojis {

  case class Emoji(
    name: Option[String],
    unified: String,
    non_qualified: Option[String],
    docomo: Option[String],
    au: Option[String],
    softbank: Option[String],
    google: Option[String],
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

    lazy val points: List[String] = unified.split("-").toList

    // https://stackoverflow.com/questions/10763730/hex-string-to-int-short-and-long-in-scala
    def getEmojiInt(unified: String): Option[Int] =
      scala.util.Try(Integer.parseInt(unified, 16)).toOption

    // lazy val emojiInt: Option[Int] = points.headOption.flatMap(getEmojiInt)

    // return only UTF-8 emojis
    lazy val emojiInt: Option[Int] = points.headOption.filter { s => s.length == 4 }.flatMap(getEmojiInt)
    lazy val emojiHexString: Option[String] = emojiInt.map(i => "%04x".format(i))
    lazy val emojiChar: Option[Char] = emojiInt.map(_.toChar)


    lazy val emojiInts: Option[List[Int]] = Traverse[List].sequence(points.map(getEmojiInt))
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

  def tweetStringToEmojiInts(s: String): Option[List[Int]] = {
    val ints: List[Int] = s.toCharArray().map(_.toInt).toList
    // val opHead: Option[Int] = ints.headOption.map(_ | 0x10000)
    // val tail = ints.tail
    // opHead.map(head => head::tail)
    Some(ints)
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
      decodedEmojis.slice(512,560).foreach { emoji =>
        println(emoji.name + "  " + emoji.unified + "  " + emoji.emojiInts.map(_.map(_.toHexString)))
      }

      println("UTF-8 emojis:")
      decodedEmojis.filter(_.emojiInt.isDefined).foreach { emoji =>
        println(emoji.name + "  " + emoji.unified + "  " + emoji.emojiHexString)
      }

      println("UTF-8 emoji chars:")
      decodedEmojis.filter(_.emojiInt.isDefined).foreach { emoji =>
        println(emoji.name + "  " + emoji.unified + "  " + emoji.emojiChar.toString)
      }
      
    }
  }
  
  println("------------------")

  // https://stackoverflow.com/a/25977288/1007926

  import java.nio.charset.Charset
  import java.nio.charset.CodingErrorAction

  // https://stackoverflow.com/a/25977288/1007926

  // val decoder = Charset.forName("UTF-16").newDecoder()


  def toSource(inputStream: java.io.InputStream): scala.io.BufferedSource = {
    println("to source")
    import java.nio.charset.Charset
    import java.nio.charset.CodingErrorAction
    val decoder = Charset.forName("UTF-16").newDecoder()
    decoder.onMalformedInput(CodingErrorAction.IGNORE)
    scala.io.Source.fromInputStream(inputStream)(decoder)
  }

  
  val exampleEmojisPath = "src/main/resources/emojis/exampleEmoji.txt"

  // https://docs.oracle.com/javase/8/docs/api/java/nio/charset/Charset.html
  // https://docs.oracle.com/javase/8/docs/api/java/io/InputStream.html

  // val exampleEmojisText: List[String] = Source.fromFile(exampleEmojisPath).getLines.toList
  val emojisStream = new java.io.FileInputStream(exampleEmojisPath)

  val emojisBufferedSource = toSource(emojisStream)

  val exampleEmojisText = emojisBufferedSource.getLines.toList

  exampleEmojisText.foreach(println(_))

  println("-----")
  
  exampleEmojisText.foreach(s => println(tweetStringToEmojiInts(s).map(_.map(_.toHexString))))

  println("-----")

  // https://stackoverflow.com/questions/5585919/creating-unicode-character-from-its-number

  // https://stackoverflow.com/questions/24968645/how-to-get-unicode-of-smiley-in-scala
  // val tongue = "\ud83d\ude03"
  // println("emoji with two code points")
  // println(tongue)

  // // val desert = ''

  // val office = "office building emoji 🏢"

  // println(office)

  // val mahjong = '\u1F00'.toString

  // println("mahjong:")
  // println(mahjong)

  // val tweets = "🎶 👂 🤑 🎒 💛 😂 👏 🐼 📸 💕"

  // // https://stackoverflow.com/questions/2220366/get-unicode-value-of-a-character

  // println(tweets)

  // // https://stackoverflow.com/questions/236097/finding-the-unicode-codepoint-of-a-character-in-gnu-emacs
  // val music = "aaa🎶zzz"
  // val music2 = "🎶"
  
  // // code point in charset: 0x1F3B6



  // // {
  // //     "name": "MULTIPLE MUSICAL NOTES",
  // //     "unified": "1F3B6",
  // //     "non_qualified": null,
  // //     "docomo": "E6FF",
  // //     "au": "E505",
  // //     "softbank": "E326",
  // //     "google": "FE814",
  // //     "image": "1f3b6.png",
  // //     "sheet_x": 9,
  // //     "sheet_y": 16,
  // //     "short_name": "notes",
  // //     "short_names": [
  // //         "notes"
  // //     ],
  
  // println("musical note:")
  // println(music)
  
  // // val musicChars = music.map(_.toInt.toHexString)
  // // println("music chars:")
  // // println(musicChars)

  // // val musicChar = 

  // val example = "1f3ec"
  // println("example")
  // println(example)
  // println("\\u"+example)

  // // https://docs.oracle.com/javase/8/docs/api/?java/lang/Integer.html
  // // https://docs.oracle.com/javase/8/docs/api/java/lang/Integer.html#valueOf-java.lang.String-int-

  // val example2 = "0x1f3ec"
  // // val example2 = "0xfffff"
  // println(example2)

  // // val exampleInt: Integer = Integer.valueOf(example2, 4)
  // // val exampleInt: Integer = Integer.getInteger(example2, 4)
  // val exampleInt: Integer = Integer.decode(example2)
  // println(exampleInt)
  // println("example int")
  // println(exampleInt.toInt.toHexString)

  // // println("music char:")
  // // println(musicChar.toString)

  // // println("music hex: ")
  // // val musicHex = musicChar.toHexString
  // // println(musicHex)

  // println("a int")
  // // val aInt = Integer.parseInt("a", 16)
  // // println(aInt)
  // println('a'.toInt.toHexString)

  // // https://stackoverflow.com/questions/28738342/convert-special-characters-to-unicode-escape-characters-scala

  // println("music int")
  // // val musicInt = Integer.parseInt(music2, 8)
  // // val musicInt = Integer.toHexString(music2 | 0x10000).subString(1)

  // // def mask(c: Char): Char = c | 0x10000
  // // val musicInt = Integer.toHexString(music2.map(mask).subString(1))
  // // println(musicInt)

  // // println(musicInt.toHexString)


  // println("tweets")
  // println(tweets)

  // println("mahjong")
  // println(mahjong.toString)

  // // https://stackoverflow.com/questions/24968645/how-to-get-unicode-of-smiley-in-scala
  // val smiley = "\u263a"
  // println(smiley)

  // val smiley2 = "☺"
  // println("another smiley")
  // println(smiley2)

  // println("smiley equality")
  // println(smiley==smiley2)

  // println("filing cabinet")

  // val filingCabinet = "\ufe0f"
  // println(filingCabinet)

  // println("smiley - filing cabinet equality check")
  // println(smiley==filingCabinet)

  

  // // BannoDemo/src/main/scala/me/peterbecich/bannodemo/twitter/stats/



}
