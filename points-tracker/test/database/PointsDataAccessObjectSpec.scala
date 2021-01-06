package database

import java.time.OffsetDateTime
import java.util.UUID

import model.{Dannon, MillerCoors, Unilever}
import org.scalatest.fixture

class PointsDataAccessObjectSpec extends fixture.FunSpec {

  class FixtureParam {
    val pointsDataAccessObject = new PointsDataAccessObject()
  }

  override protected def withFixture(test: OneArgTest) = test(new FixtureParam())

  describe("PointsDataAccessObject") {
    describe("addNewUser") {
      it("should add a new user with empty points") { fixture =>
        import fixture._
        val userId = UUID.randomUUID()
        assert(pointsDataAccessObject.addNewUser(userId).isSuccess)
        val result = pointsDataAccessObject.users.get(userId)
        assert(result.isDefined)
        assert(result.get.points.isEmpty)
      }

      it("should fail if user already exists") { fixture =>
        import fixture._
        val userId = UUID.randomUUID()
        assert(pointsDataAccessObject.addNewUser(userId).isSuccess)
        assert(pointsDataAccessObject.addNewUser(userId).isFailure)
      }
    }

    it("should add new points and dequeue should return the oldest points first") { fixture =>
      import fixture._
      val userId = UUID.randomUUID()
      assert(pointsDataAccessObject.addNewUser(userId).isSuccess)

      val oldestPoints = Points(10, OffsetDateTime.now().minusWeeks(2), Unilever)
      val middlePoints = Points(10, OffsetDateTime.now().minusWeeks(1), Unilever)
      val newestPoints = Points(10, OffsetDateTime.now(), Unilever)

      List(middlePoints, oldestPoints, newestPoints).foreach(p =>
        assert(pointsDataAccessObject.addPositivePoints(userId, p.date, p.company, p.value).isSuccess)
      )

      val firstResult = pointsDataAccessObject.users.get(userId).get.points.dequeue()
      val secondResult = pointsDataAccessObject.users.get(userId).get.points.dequeue()
      val thirdResult = pointsDataAccessObject.users.get(userId).get.points.dequeue()

      assert(firstResult == oldestPoints)
      assert(secondResult == middlePoints)
      assert(thirdResult == newestPoints)
    }

    it("should handle partial usage of points and use oldest points first") { fixture =>
      import fixture._

      val userId = UUID.randomUUID()
      assert(pointsDataAccessObject.addNewUser(userId).isSuccess)

      val inputOne = Points(100, OffsetDateTime.now().minusWeeks(3), Dannon)
      val inputTwo = Points(200, OffsetDateTime.now().minusWeeks(2), Unilever)
      val inputThree = Points(10000, OffsetDateTime.now().minusWeeks(1), MillerCoors)
      val inputFour = Points(1000, OffsetDateTime.now(), Dannon)

      List(inputOne, inputTwo, inputThree, inputFour).foreach(p =>
        assert(pointsDataAccessObject.addPositivePoints(userId, p.date, p.company, p.value).isSuccess)
      )

      val result = pointsDataAccessObject.deductPoints(userId, 5000).get
      assert(result.length == 3)
      assert(result.filter(_.company == Dannon).head.value == -100)
      assert(result.filter(_.company == Unilever).head.value == -200)
      assert(result.filter(_.company == MillerCoors).head.value == -4700)

      val remaining = pointsDataAccessObject.users.get(userId).get.points
      assert({
        val next = remaining.dequeue()
        next.value == 5300 && next.company == MillerCoors
      })
      assert({
        val next = remaining.dequeue()
        next.value == 1000 && next.company == Dannon
      })
    }

    it("should fail to add points if the user doesn't exist") { fixture =>
      import fixture._

      assert(pointsDataAccessObject.addPositivePoints(
        UUID.randomUUID(),
        OffsetDateTime.now(),
        Dannon,
        100
      ).isFailure)
    }

    it("should fail to deduct points if the user doesn't exist") { fixture =>
      import fixture._

      assert(pointsDataAccessObject.deductPoints(
        UUID.randomUUID(),
        100
      ).isFailure)
    }

    it("should use older points first even if they were added later and remove used points") { fixture =>
      import fixture._

      val userId = UUID.randomUUID()
      assert(pointsDataAccessObject.addNewUser(userId).isSuccess)

      val oldest = Points(100, OffsetDateTime.now().minusWeeks(1), Dannon)
      val newest = Points(200, OffsetDateTime.now(), Unilever)

      List(newest, oldest).foreach(p =>
        assert(pointsDataAccessObject.addPositivePoints(userId, p.date, p.company, p.value).isSuccess)
      )

      val result = pointsDataAccessObject.deductPoints(userId, 100).get
      assert(result.length == 1)
      assert(result.filter(_.company == oldest.company).head.value == -1 * oldest.value)

      val remaining = pointsDataAccessObject.users.get(userId).get.points
      assert({
        val next = remaining.dequeue()
        next.value == newest.value && next.company == newest.company
      })
    }

  }

}
