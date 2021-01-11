package model.http

import java.util.UUID

import play.api.libs.json.Json

case class AddNewUserResponse(userId: UUID)

object AddNewUserResponse {
  implicit val format = Json.format[AddNewUserResponse]
}
