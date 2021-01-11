package model

import java.time.OffsetDateTime

import model.Company.Company
import play.api.libs.json.Json

case class Points(
   value: Int,
   date: OffsetDateTime,
   company: Company
 )

object Points {
  implicit val format = Json.format[Points]
}
