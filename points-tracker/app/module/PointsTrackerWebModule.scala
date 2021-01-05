package module

import controllers.PointsTrackerController
import play.api.mvc.ControllerComponents

trait PointsTrackerWebModule {

  lazy val homeController = new PointsTrackerController(controllerComponents)

  val controllerComponents: ControllerComponents

}
