package module

import akka.actor.{ActorSystem, Props}
import controllers.{PointsTrackerController, PointsTrackerControllerImpl}
import database.PointsDataAccessActor
import play.api.mvc.ControllerComponents

import scala.concurrent.ExecutionContext

trait PointsTrackerWebModule {

  val controllerComponents: ControllerComponents
  implicit val ec: ExecutionContext
  val actorSystem: ActorSystem

  lazy val pointsDataAccessActor = actorSystem.actorOf(Props(classOf[PointsDataAccessActor], ec), name = "pointsDataAccessActor")
  lazy val pointsTrackerController: PointsTrackerController = new PointsTrackerControllerImpl(pointsDataAccessActor, controllerComponents)
}
