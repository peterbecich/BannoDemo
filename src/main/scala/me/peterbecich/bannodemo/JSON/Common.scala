package me.peterbecich.bannodemo.JSON

import com.danielasfregola.twitter4s.entities.Tweet
import io.circe.Encoder
import io.circe._
import io.circe.generic.semiauto._
import io.circe.literal._
import io.circe.syntax._
import java.time.LocalDateTime
import java.time.ZonedDateTime

object Common {

  implicit val ZonedDateTimeEncoder: Encoder[ZonedDateTime] =
    Encoder.instance { zonedDateTime => json"""${zonedDateTime.toString}""" }

  implicit val LocalDateTimeEncoder: Encoder[LocalDateTime] =
    Encoder.instance { localDateTime => json"""${localDateTime.toString}""" }

}
