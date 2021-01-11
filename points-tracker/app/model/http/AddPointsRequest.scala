package model.http

import java.time.OffsetDateTime
import java.util.UUID

import database.PointsDataAccessActor.{AddNegativePoints, AddPositivePoints, PointsDataAccessAction}
import model.Company.Company
import model.Points
import play.api.libs.json.Json

import scala.util.{Failure, Success, Try}


case class AddPointsRequest(
   userId: UUID,
   value: Int,
   company: Company
 )

object AddPointsRequest {
  implicit val addPointsFormat = Json.format[AddPointsRequest]

  def toDatabaseRequest(req: AddPointsRequest): Try[PointsDataAccessAction] = {
    if (req.value == 0) Failure(new Exception("Cannot add zero points"))
    else if (req.value < 0) Success(AddNegativePoints(req.userId, Points(req.value, OffsetDateTime.now(), req.company)))
    else Success(AddPositivePoints(req.userId, Points(req.value, OffsetDateTime.now(), req.company)))
  }
}
