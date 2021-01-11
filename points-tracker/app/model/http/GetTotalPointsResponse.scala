package model.http

import model.Company.Company
import play.api.libs.json.Json

case class TotalPoints(company: Company, value: Int)
object TotalPoints {
  implicit val format = Json.format[TotalPoints]
}

object GetTotalPointsResponse {
  type GetTotalPointsResponse = Seq[TotalPoints]

  def dataToHttp(data: Map[Company, Int]): GetTotalPointsResponse = {
    data.toList.map(d => TotalPoints(d._1, d._2))
  }
}
