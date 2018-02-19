package me.peterbecich.bannodemo.emojis

import cats._
import cats.implicits._
import cats.syntax.all._
import io.circe.Encoder
import io.circe._
import io.circe.generic.semiauto._
import io.circe.literal._
import io.circe.syntax._

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
    short_name: String,
    short_names: List[String],
    category: String,
    added_in: String
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
  lazy val retrieveEmojis: Either[Error, EmojisCollection] = {
    val emojisPath = "/srv/emojis/emoji_pretty.json"
    val emojisText = Source.fromFile(emojisPath).getLines.mkString

    val eUnfiltered: Either[Error, EmojisCollection] = decode[EmojisCollection](emojisText)

    val numeric = List('0','1','2','3','4','5','6','7','8','9','#')

    eUnfiltered.map { (lEmoji: EmojisCollection)  =>
      lEmoji
        .filter { emoji => emoji.emojiChar.isDefined }
        .filter { emoji => numeric.contains(emoji.emojiChar.get) == false } // unsafe
    }
  }

  lazy val retrieveUTF8Emojis: Either[Error, EmojisCollection] =
    retrieveEmojis.map { emojis =>
      emojis.filter(_.emojiChar.isDefined)
    }

  lazy val utf8EmojisChars: List[String] =
    retrieveUTF8Emojis.fold(_ => List(), ll => ll).map(_.emojiChar.get.toString)

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

}
