package module

import com.typesafe.config.Config
import play.api.{Application, ApplicationLoader, BuiltInComponentsFromContext, LoggerConfigurator}
import play.api.ApplicationLoader.Context
import play.filters.HttpFiltersComponents
import router.Routes
import pureconfig._

import scala.concurrent.ExecutionContext

class PointsTrackerApplicationLoader extends ApplicationLoader {
  def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment, context.initialConfiguration, Map.empty)
    }

    new PointsTrackerApplicationModule(context).application
  }

}

class PointsTrackerApplicationModule(context: Context)
  extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with controllers.AssetsComponents
    with PointsTrackerWebModule {

  implicit val ec: ExecutionContext = actorSystem.dispatcher

  lazy val config = ConfigSource.default.loadOrThrow[Config]

  lazy val router = new Routes(httpErrorHandler, pointsTrackerController, assets, "/")
}
