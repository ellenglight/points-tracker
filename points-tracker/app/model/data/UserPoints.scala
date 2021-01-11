package model.data

import java.time.OffsetDateTime

import model.Company.Company
import model.Points

import scala.collection.immutable.Queue

/**
 * Represents a single user's account.
 *
 * Assumes that points are added in a synchronous manner, ie that the date of the
 * points is the date that the request was received. If the points are added asynchronously
 * and the date is specified by the request, then we could use a PriorityQueue instead
 * with older dates having higher priority. We would also need to check that users do
 * not spend "future" points.
 *
 * The fields of this class are mutable to allow [[database.UserActor]] and [[database.PointsDataAccessActor]]
 * to share the reference.
 *
 * @param points immutable FIFO queue representing positive point values the user can spend
 * @param totalNegativePoints immutable map representing points that the user "owes" the company
 */
class UserPoints(
  var points: Queue[Points],
  var totalNegativePoints: Map[Company, Int]
)

object UserPoints {
  def apply(): UserPoints = new UserPoints(Queue.empty[Points], Map.empty[Company, Int])
}
