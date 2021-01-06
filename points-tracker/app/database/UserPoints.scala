package database

import scala.collection.mutable
import java.time.OffsetDateTime

import model.Company

// Uses a mutable PriorityQueue because I couldn't find an immutable implementation unfortunately
class UserPoints(
  var points: mutable.PriorityQueue[Points]
)

object UserPoints {
  // Older points should have higher priority
  implicit val pointsOrdering: Ordering[Points] =  new Ordering[Points] {
    override def compare(x: Points, y: Points): Int = y.date.compareTo(x.date)
  }

  def apply(): UserPoints = new UserPoints(new mutable.PriorityQueue[Points]())
}


case class Points(
  value: Int,
  date: OffsetDateTime,
  company: Company
)
