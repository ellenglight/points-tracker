package database

import java.time.OffsetDateTime
import java.util.UUID

import model.Company

import scala.collection.mutable.HashMap
import scala.util.{Failure, Success, Try}

class PointsDataAccessObject() {

  val users: HashMap[UUID,UserPoints] = HashMap[UUID,UserPoints]()

  def addNewUser(userId: UUID): Try[Unit] = {
    users.synchronized {
      users.get(userId) match {
        case Some(_) => Failure(new RuntimeException(s"Could not add new user: User $userId already exists"))
        case None => {
          val newUser = UserPoints()
          users.put(userId, newUser)
          Success(())
        }
      }
    }
  }

  def withUser[T](userId: UUID, errorMessage: String)(f: UserPoints => T): Try[T] = {
    users.get(userId) match {
      case Some(user) => Success(f(user))
      case None => Failure(new RuntimeException(errorMessage))
    }
  }

  def addPositivePoints(userId: UUID, date: OffsetDateTime, company: Company, value: Int): Try[Unit] = {
    val newPoints = Points(value, date, company)
    withUser(userId, s"Could not add points: User $userId does not exist") { user =>
      // Only one request should edit a given user's points at a time
      user.synchronized {
        Success(user.points.enqueue(newPoints))
      }
    }
  }

  def addNegativePoints(userId: UUID, date: OffsetDateTime, company: Company, value: Int): Try[Unit] = ???

  def deductPoints(userId: UUID, value: Int): Try[Seq[Points]] = {

    withUser(userId, s"Could not deduct points: User $userId does not exist"){ user =>
      // Only one request should edit a given user's points at a time
      user.synchronized {
        // Only the dequeue and dequeueAll methods return the points in priority order, the iterator defined by PriorityQueue returns the
        // elements in an undefined order
        def loopOverQueue(remaining: Int, soFar: Map[Company, Int]): Map[Company, Int] = {
          if (remaining > 0) {
            val next: Points = user.points.dequeue()

            // The user is only spending a fraction of these points
            if (next.value > remaining) {
              val unusedPoints = next.value - remaining
              user.points.enqueue(next.copy(value = unusedPoints))
              soFar.get(next.company).fold(soFar + (next.company -> remaining))(c => soFar + (next.company -> (c + remaining)))
            }

            // The user is spending all of these points
            else loopOverQueue(
              remaining - next.value,
              soFar.get(next.company).fold(soFar + (next.company -> next.value))(c => soFar + (next.company -> (c + next.value)))
            )
          }
          else soFar
        }

        loopOverQueue(value, Map[Company, Int]()).toList.map(companyAndValue =>
          Points(company = companyAndValue._1, value = companyAndValue._2 * -1, date = OffsetDateTime.now())
        )
      }
    }
  }
}


