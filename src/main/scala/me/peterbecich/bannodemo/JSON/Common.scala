package me.peterbecich.bannodemo.JSON

import java.time.ZonedDateTime
import java.time.LocalDateTime

import com.danielasfregola.twitter4s.entities.Tweet

import io.circe._
import io.circe.Encoder
import io.circe.syntax._
import io.circe.literal._
import io.circe.generic.semiauto._

object Common {

  implicit val ZonedDateTimeEncoder: Encoder[ZonedDateTime] =
    Encoder.instance { zonedDateTime => json"""${zonedDateTime.toString}""" }

  implicit val LocalDateTimeEncoder: Encoder[LocalDateTime] =
    Encoder.instance { localDateTime => json"""${localDateTime.toString}""" }

}
