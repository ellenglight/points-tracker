package controllers

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import database.PointsDataAccessActor.{AddNewUser, GetTotalPoints}
import model.Company.Company
import model.Error.{LowBalanceException, UserDoesNotExistException}
import model.Points
import model.http.{AddNewUserResponse, AddPointsRequest, DeductPointsRequest, GetTotalPointsResponse}
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.mvc._

import scala.util.control.NonFatal
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait PointsTrackerController {
  /**
   * Add points for user
   * Request: Json body matching [[AddPointsRequest]]
   * Response: 200, 400, or 500 status
   */
  def addPoints(userId: UUID): EssentialAction

  /**
   * Get the total points for the user
   * Response: Json body matching [[GetTotalPointsResponse]]
   */
  def getTotalPoints(userId: UUID): EssentialAction

  /**
   * Deduct points for user
   * Request: Json body matching [[DeductPointsRequest]]
   * Response: Json body matching [[Seq[Points]]]
   */
  def deductPoints(userId: UUID): EssentialAction

  /**
   * Add a new user
   * Response: Json body matching [[AddNewUserResponse]]
   */
  def addNewUser(): EssentialAction
}

class PointsTrackerControllerImpl(
   pointsDataAccessActor: ActorRef,
   val controllerComponents: ControllerComponents
 )(implicit val ec: ExecutionContext) extends BaseController with PointsTrackerController {

  // TODO: Move this to a config object
  implicit val akkaActorTimeout: Timeout = Timeout(4, TimeUnit.SECONDS)

  override def addPoints(userId: UUID): EssentialAction = Action(parse.tolerantJson).async { request =>
    request.body.validate[AddPointsRequest] match {
      case JsSuccess(value, _) => AddPointsRequest.toDatabaseRequest(value) match {
        case Success(r) => {
          (pointsDataAccessActor ? r).mapTo[Unit].map(_ => Ok).recover {
          case _: UserDoesNotExistException => BadRequest("User does not exist")
          case NonFatal(e) => InternalServerError
        }}
        case Failure(e) => Future.successful(BadRequest(e.getMessage))
      }
      case JsError(_) => Future.successful(BadRequest("Invalid JSON"))
    }
  }

  override def getTotalPoints(userId: UUID): EssentialAction = Action.async { _ =>
    (pointsDataAccessActor ? GetTotalPoints(userId)).mapTo[Map[Company, Int]].map(result =>
      Ok(Json.toJson(GetTotalPointsResponse.dataToHttp(result)))
    ).recover {
      case NonFatal(e) => InternalServerError
    }
  }

  override def deductPoints(userId: UUID): EssentialAction = Action.async(parse.tolerantJson) { request =>
    request.body.validate[DeductPointsRequest] match {
      case JsSuccess(value, _) => DeductPointsRequest.toDatabaseRequest(value) match {
        case Success(r) => {
          (pointsDataAccessActor ? r).mapTo[Seq[Points]].map(r => Ok(Json.toJson(r))).recover {
            case _: UserDoesNotExistException => BadRequest("User does not exist")
            case _: LowBalanceException => BadRequest("User does not have enough points")
            case NonFatal(e) => InternalServerError
          }}
        case Failure(e) => Future.successful(BadRequest(e.getMessage))
      }
      case JsError(_) => Future.successful(BadRequest("Invalid JSON"))
    }
  }

  override def addNewUser(): EssentialAction = Action.async { _ =>
    (pointsDataAccessActor ? AddNewUser).mapTo[UUID].map(userId => Ok(Json.toJson(AddNewUserResponse(userId))))
      .recover {
      case NonFatal(e) => InternalServerError
    }
  }
}
