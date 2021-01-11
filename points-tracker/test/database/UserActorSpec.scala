package database

import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import database.UserActor.{AddNegativePoints, AddPositivePoints, DeductPoints, GetTotalPoints}
import model.Company.{Company, Dannon, MillerCoors, Unilever}
import model.Error.LowBalanceException
import model.Points
import model.data.UserPoints
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}

import scala.collection.immutable.Queue

class UserActorSpec
  extends TestKit(ActorSystem("UserActorSpec"))
    with FlatSpecLike
    with ImplicitSender
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with BeforeAndAfterAll {

  class FixtureParam {
    implicit val ec = system.dispatcher
    implicit val messageTimeout = Timeout(5, TimeUnit.SECONDS)
    val userPoints = UserPoints()
    val userActor = system.actorOf(Props(classOf[UserActor], userPoints), name = UUID.randomUUID().toString)
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "UserActor" should "add positive and negative points" in {
    val fixture = new FixtureParam()
    import fixture._
    val points = Points(10, OffsetDateTime.now(), Dannon)
    (userActor ? AddPositivePoints(points)).mapTo[Unit].futureValue
    (userActor ? AddNegativePoints(Points(-10, OffsetDateTime.now(), Dannon))).mapTo[Unit].futureValue
    assert(userPoints.points == Queue(points))
    assert(userPoints.totalNegativePoints == Map(Dannon -> -10))
  }

  it should "get the total points" in {
    val fixture = new FixtureParam()
    import fixture._
    userPoints.points = Queue.empty[Points]
      .enqueue(Points(10, OffsetDateTime.now(), Dannon))
      .enqueue(Points(10, OffsetDateTime.now(), Dannon))
      .enqueue(Points(30, OffsetDateTime.now(), Unilever))
    userPoints.totalNegativePoints = Map(Dannon -> -1)

    val result = (userActor ? GetTotalPoints()).mapTo[Map[Company, Int]].futureValue
    assert(result == Map(Dannon -> 19, Unilever -> 30, MillerCoors -> 0))
  }

  it should "deduct the oldest points first" in {
    val fixture = new FixtureParam()
    import fixture._

    val initial = Queue.empty[Points]
      .enqueue(Points(10, OffsetDateTime.now(), Dannon))
      .enqueue(Points(10, OffsetDateTime.now(), MillerCoors))
      .enqueue(Points(30, OffsetDateTime.now(), Unilever))
    userPoints.points = initial

    // Use part of the points
    val resultPartial = (userActor ? DeductPoints(5)).mapTo[Seq[Points]].futureValue
    assert(resultPartial.length == 1)
    assert(resultPartial.filter(_.company == Dannon).head.value == -5)
    assert(userPoints.points.dequeue._1.value == 5 && userPoints.points.dequeue._1.company == Dannon)
    assert(userPoints.points.dequeue._2 == initial.dequeue._2) // rest should remain unchanged

    // Use all of the points
    val resultAll = (userActor ? DeductPoints(5)).mapTo[Seq[Points]].futureValue
    assert(resultAll.length == 1)
    assert(resultAll.filter(_.company == Dannon).head.value == -5)
    assert(userPoints.points.dequeue._1.value == 10 && userPoints.points.dequeue._1.company == MillerCoors)
    assert(userPoints.points.dequeue._2 == initial.dequeue._2.dequeue._2)
  }

  it should "use the oldest points to account for a negative point balance for that company (entire debt)" in {
    val fixture = new FixtureParam()
    import fixture._

    val initialPoints = Queue.empty[Points]
      .enqueue(Points(10, OffsetDateTime.now(), Dannon))
      .enqueue(Points(10, OffsetDateTime.now(), MillerCoors))
      .enqueue(Points(30, OffsetDateTime.now(), Unilever))
    userPoints.points = initialPoints
    val initialNegative = Map(Dannon -> -2)
    userPoints.totalNegativePoints = initialNegative

    // Pay off entire debt with first points object with some points left over
    val result = (userActor ? DeductPoints(5)).mapTo[Seq[Points]].futureValue
    assert(result.length == 1)
    assert(result.filter(_.company == Dannon).head.value == -5)
    assert(userPoints.points.dequeue._1.value == 3 && userPoints.points.dequeue._1.company == Dannon)
    assert(userPoints.points.dequeue._2 == initialPoints.dequeue._2) // rest should remain unchanged
    assert(userPoints.totalNegativePoints.isEmpty)
  }

  it should "use the oldest points to account for a negative point balance for that company (partial debt)" in {
    val fixture = new FixtureParam()
    import fixture._

    val initialPoints = Queue.empty[Points]
      .enqueue(Points(10, OffsetDateTime.now(), Dannon))
      .enqueue(Points(10, OffsetDateTime.now(), Dannon))
      .enqueue(Points(30, OffsetDateTime.now(), Unilever))
    userPoints.points = initialPoints
    val initialNegative = Map(Dannon -> -15)
    userPoints.totalNegativePoints = initialNegative

    // Use entire points object to pay off the debt with no points left over
    val result = (userActor ? DeductPoints(5)).mapTo[Seq[Points]].futureValue
    assert(result.length == 1)
    assert(result.filter(_.company == Dannon).head.value == -5)
    assert(userPoints.points.dequeue._1.value == 30 && userPoints.points.dequeue._1.company == Unilever)
    assert(userPoints.points.dequeue._2.isEmpty)
    assert(userPoints.totalNegativePoints.isEmpty)
  }

  it should "use points from multiple companies if one isn't enough" in {
    val fixture = new FixtureParam()
    import fixture._

    val initialPoints = Queue.empty[Points]
      .enqueue(Points(10, OffsetDateTime.now(), Dannon))
      .enqueue(Points(10, OffsetDateTime.now(), MillerCoors))
      .enqueue(Points(30, OffsetDateTime.now(), Unilever))
    userPoints.points = initialPoints
    val initialNegative = Map(MillerCoors -> -5)
    userPoints.totalNegativePoints = initialNegative

    val result = (userActor ? DeductPoints(25)).mapTo[Seq[Points]].futureValue
    assert(result.length == 3)
    assert(result.filter(_.company == Dannon).head.value == -10)
    assert(result.filter(_.company == MillerCoors).head.value == -5) // Needed to pay off the negative balance
    assert(result.filter(_.company == Unilever).head.value == -10)
    assert(userPoints.points.dequeue._1.value == 20 && userPoints.points.dequeue._1.company == Unilever)
    assert(userPoints.points.dequeue._2.isEmpty)
    assert(userPoints.totalNegativePoints.isEmpty)
  }

  it should "ignore negative points from other companies if their points are not needed" in {
    val fixture = new FixtureParam()
    import fixture._

    val initialPoints = Queue.empty[Points]
      .enqueue(Points(10, OffsetDateTime.now(), MillerCoors))
      .enqueue(Points(10, OffsetDateTime.now(), Dannon))
      .enqueue(Points(30, OffsetDateTime.now(), Unilever))
    userPoints.points = initialPoints
    val initialNegative = Map(Dannon -> -5)
    userPoints.totalNegativePoints = initialNegative

    val result = (userActor ? DeductPoints(5)).mapTo[Seq[Points]].futureValue
    assert(result.length == 1)
    assert(result.filter(_.company == MillerCoors).head.value == -5)
    assert(userPoints.points.dequeue._1.value == 5 && userPoints.points.dequeue._1.company == MillerCoors)
    assert(userPoints.points.dequeue._2 == initialPoints.dequeue._2) // rest should remain unchanged
    assert(userPoints.totalNegativePoints == initialNegative)
  }

  it should "fail if the user doesn't have enough points and should not update the user's account" in {
    val fixture = new FixtureParam()
    import fixture._

    val initialPoints = Queue.empty[Points]
      .enqueue(Points(10, OffsetDateTime.now(), MillerCoors))
      .enqueue(Points(10, OffsetDateTime.now(), Dannon))
      .enqueue(Points(30, OffsetDateTime.now(), Unilever))
    userPoints.points = initialPoints
    val initialNegative = Map(Dannon -> -5)
    userPoints.totalNegativePoints = initialNegative

    val result = (userActor ? DeductPoints(10000000)).mapTo[Seq[Points]].failed.futureValue
    assert(result.isInstanceOf[LowBalanceException])
    // should remain unchanged
    assert(userPoints.points == initialPoints)
    assert(userPoints.totalNegativePoints == initialNegative)
  }
}
