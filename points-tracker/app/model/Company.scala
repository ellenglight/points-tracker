package model

import play.api.libs.json.{Format, Json}

object Company extends Enumeration {
  type Company = Value

  val Dannon = Value("DANNON")
  val Unilever = Value("UNILEVER")
  val MillerCoors = Value("MILLERCOORS")

  implicit val companyFormat = Json.formatEnum(this)

}

