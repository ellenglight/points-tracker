package model.http

import java.util.UUID

import database.PointsDataAccessActor.DeductPoints
import play.api.libs.json.Json

import scala.util.{Failure, Success, Try}

case class DeductPointsRequest(userId: UUID, value: Int)

object DeductPointsRequest {
  implicit val format = Json.format[DeductPointsRequest]

  def toDatabaseRequest(httpRequest: DeductPointsRequest): Try[DeductPoints] = {
    if (httpRequest.value > 0) Success(DeductPoints(httpRequest.userId, httpRequest.value))
    else Failure(new Exception("Points to deduct must be a positive value"))
  }
}
