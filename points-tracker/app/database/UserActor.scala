package database

import java.time.{OffsetDateTime, ZoneOffset}

import akka.actor.Actor
import model.{Company, Points}
import model.Company.Company
import model.Error.LowBalanceException
import model.data.UserPoints

import scala.collection.immutable.Queue
import scala.util.{Failure, Success, Try}

/**
 * This is an Akka actor (https://doc.akka.io/docs/akka/current/actors.html) that processes
 * requests to access/modify the account for a single user.
 *
 * @param user The user account data
 */
class UserActor(
   user: UserPoints
 ) extends Actor {
  def addPositivePoints(newPoints: Points): Unit = {
    val updatedQueue = user.points.enqueue(newPoints)
    user.points = updatedQueue
  }

  def addNegativePoints(newPoints: Points): Unit = {
    // TODO Do not allow a company to have a negative balance?
    val updatedNegativePoints = user.totalNegativePoints + (newPoints.company -> (user.totalNegativePoints.getOrElse(newPoints.company, 0) + newPoints.value))
    user.totalNegativePoints = updatedNegativePoints
  }

  def getTotalPoints(): Map[Company, Int] = {
    // TODO Performance improvement: Could maintain Map[Company, Int] for the total number of points to make getTotalPoints constant time instead of linear in the number of points objects
    val companiesWithPoints = user.points
      .foldLeft(Map.empty[Company, Int])((total, next) => total + (next.company -> (total.getOrElse(next.company, 0) + next.value)))
      .transform((company, value) => value + user.totalNegativePoints.getOrElse(company, 0))
    // Fill in remaining companies with 0
    Company.values.foldLeft(companiesWithPoints)((totals, next) => totals + (next -> totals.getOrElse(next, 0)))
  }

  def deductPoints(value: Int): Try[Seq[Points]] = {

    /**
     * Updates the points and the negative point totals if the user "owes" points to the company (ie the company
     * has negative points)
     */
    def payBackNegativePoints(points: Points, negativePointTotals: Map[Company, Int]): (Points, Map[Company, Int]) = {
      negativePointTotals.get(points.company) match {
        case Some(debt) => {
          // This points object will cover this debt completely
          if (points.value >= debt.abs) (points.copy(value = points.value + debt), negativePointTotals - points.company)
          // This points object only partially covers this debt
          else (points.copy(value = 0), negativePointTotals + (points.company -> (debt + points.value)))
        }
        case None => (points, negativePointTotals) // No negative points so no change
      }
    }

    def loopOverQueue(
     remaining: Int,
     soFar: Map[Company, Int],
     restOfQueue: Queue[Points],
     updatedTotalNegativePoints: Map[Company, Int]
   ): Try[(Map[Company, Int], Queue[Points], Map[Company, Int])] = {
      val nextOpt: Option[(Points, Queue[Points])] = restOfQueue.dequeueOption
      nextOpt match {
        case Some((next, rest)) => {
          // Assume that the user pays back negative points to a company using the oldest points for this company first
          val (nextAfterDebt, totalNegativePointsAfterDebt) = payBackNegativePoints(next, updatedTotalNegativePoints)

          // This points object was used up when paying off "debt" (negative points) for the company
          if (nextAfterDebt.value == 0) loopOverQueue(remaining, soFar, rest, totalNegativePointsAfterDebt)
          // This points object has positive points to spend even after paying off "debt"
          else {
            // The user is only spending a fraction of these points
            if (nextAfterDebt.value > remaining) {
              val unusedPoints = nextAfterDebt.value - remaining
              val updatedQueue = restOfQueue.map { p =>
                if (p == next) p.copy(value = unusedPoints)
                else p
              }
              val updatedSoFar = soFar + (nextAfterDebt.company -> (soFar.getOrElse(nextAfterDebt.company, 0) + remaining))
              Success((updatedSoFar, updatedQueue, totalNegativePointsAfterDebt))
            }
            // The user is spending all of these points
            else {
              // Have exactly enough points
              if (nextAfterDebt.value == remaining) {
                Success((
                  soFar + (nextAfterDebt.company -> (soFar.getOrElse(nextAfterDebt.company, 0) + remaining)),
                  rest,
                  totalNegativePointsAfterDebt
                ))}
              // Need to use more points
              else {
                loopOverQueue(
                  remaining - nextAfterDebt.value,
                  soFar + (nextAfterDebt.company -> (soFar.getOrElse(nextAfterDebt.company, 0) + nextAfterDebt.value)),
                  rest,
                  totalNegativePointsAfterDebt
                )}
            }
          }
        }
        case None => Failure(new LowBalanceException("User does not have enough points to deduct value"))
      }
    }

    loopOverQueue(value, Map.empty[Company, Int], user.points, user.totalNegativePoints) match {
      case Success((deductedPoints, updatedQueue, updatedNegativePoints)) => {
        // Only update mutable state if deduction was successful
        user.totalNegativePoints = updatedNegativePoints
        user.points = updatedQueue
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        Success(deductedPoints.toList.map(companyAndValue =>
          Points(company = companyAndValue._1, value = companyAndValue._2 * -1, date = now))
        )
      }
      case Failure(e) => Failure(e)
    }
  }


  override def receive: Receive = {
    case UserActor.AddPositivePoints(points) => {
      addPositivePoints(points)
      sender() ! akka.actor.Status.Success(())
    }
    case UserActor.AddNegativePoints(points) => {
      addNegativePoints(points)
      sender() ! akka.actor.Status.Success(())
    }
    case UserActor.GetTotalPoints() => {
      val result = getTotalPoints()
      sender() ! akka.actor.Status.Success(result)
    }
    case UserActor.DeductPoints(value) => {
      deductPoints(value) match {
        case Success(r) => sender() ! akka.actor.Status.Success(r)
        case Failure(e) => sender() ! akka.actor.Status.Failure(e)
      }
    }
  }
}

object UserActor {

  /**
   * All request types that the [[UserActor]] can process
   */
  sealed trait UserAction
  case class AddPositivePoints(points: Points) extends UserAction
  case class AddNegativePoints(points: Points) extends UserAction
  case class GetTotalPoints() extends UserAction
  case class DeductPoints(value: Int) extends UserAction
}
