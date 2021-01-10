package database

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import model.Company.Company
import model.Error.UserDoesNotExistException
import model.{Points, UserPoints}

import scala.collection.mutable.HashMap
import scala.concurrent.{ExecutionContext, Future}

/**
 * This is an Akka actor (https://doc.akka.io/docs/akka/current/actors.html)
 * that represents an in-memory data store for all user accounts. The user accounts are
 * represented by a mutable HashMap where the keys are the user ids and the values are [[UserPoints]].
 *
 * This actor can add new users, as well as access the account for each user. To make sure that each user account
 * is only accessed/modified by one request at a time, this actor forwards requests to a different actor for each user.
 * Since Akka actors process messages one at a time, this will ensure that each user account will only be accessible
 * to one request at a time. However, since requests to another actor are sent asynchronously, requests for different
 * user accounts can be processed concurrently.
 *
 * The first time a user account is modified/accessed, this actor will create a child actor whose name is the user id.
 * Since the name of an actor must be unique, there can only be one actor per user account and this actor will get reused.
 */
class PointsDataAccessActor(implicit val ec: ExecutionContext) extends Actor {

  // TODO this should be in a config file
  implicit val timeout = Timeout(3, TimeUnit.SECONDS)

  val userData: HashMap[UUID,UserPoints] = HashMap[UUID, UserPoints]()

  def addNewUser(): UUID = {
    val userId = UUID.randomUUID()
    val newUser = UserPoints()
    userData.put(userId, newUser)
    userId
  }

  def getUserActor(userId: UUID, userData: UserPoints): ActorRef = {
    context.child(userId.toString) match {
      case Some(actor) => actor
      case None => context.actorOf(Props(classOf[UserActor], userData), name = userId.toString)
    }
  }

  def withUser(userId: UUID)(sendMessage: ActorRef => Future[Unit]) = {
    userData.get(userId) match {
      case Some(user) => {
        val actor = getUserActor(userId, user)
        sendMessage(actor)
      }
      case None => {
        sender() ! akka.actor.Status.Failure(new UserDoesNotExistException(s"User $userId does not exist"))
      }
    }
  }

  override def receive: Receive = {
    case PointsDataAccessActor.AddNewUser => {
      val result = addNewUser()
      sender() ! result
    }
    case PointsDataAccessActor.AddPositivePoints(userId, points) => {
      // Save reference to sender so it can be safely referenced while mapping over a future
      val savedSender = sender()
      withUser(userId) { actor =>
        (actor ? UserActor.AddPositivePoints(points)).mapTo[Unit].map(_ => {
          savedSender ! akka.actor.Status.Success(())
        })
      }
    }
    case PointsDataAccessActor.AddNegativePoints(userId, points) => {
      // Save reference to sender so it can be safely referenced while mapping over a future
      val savedSender = sender()
      withUser(userId) { actor =>
        (actor ? UserActor.AddNegativePoints(points)).mapTo[Unit].map(_ => {
          savedSender ! akka.actor.Status.Success(())
        })
      }
    }
    case PointsDataAccessActor.GetTotalPoints(userId) => {
      // Save reference to sender so it can be safely referenced while mapping over a future
      val savedSender = sender()
      withUser(userId) { actor =>
        (actor ? UserActor.GetTotalPoints()).mapTo[Map[Company, Int]].map(r => {
          savedSender ! akka.actor.Status.Success(r)
        })
      }
    }
    case PointsDataAccessActor.DeductPoints(userId, value) => {
      // Save reference to sender so it can be safely referenced while mapping over a future
      val savedSender = sender()
      withUser(userId) { actor =>
        (actor ? UserActor.DeductPoints(value)).mapTo[Seq[Points]].map(r => {
          savedSender ! akka.actor.Status.Success(r)
        }).recover { e =>
          savedSender ! akka.actor.Status.Failure(e)
        }
      }
    }
  }
}

object PointsDataAccessActor {

  /**
   * Requests that can be processed by the [[PointsDataAccessActor]]
   */
  sealed trait PointsDataAccessAction
  case object AddNewUser extends PointsDataAccessAction
  case class AddPositivePoints(userId: UUID, points: Points) extends PointsDataAccessAction
  case class AddNegativePoints(userId: UUID, points: Points) extends PointsDataAccessAction
  case class GetTotalPoints(userId: UUID) extends PointsDataAccessAction
  case class DeductPoints(userId: UUID, value: Int) extends PointsDataAccessAction

}
