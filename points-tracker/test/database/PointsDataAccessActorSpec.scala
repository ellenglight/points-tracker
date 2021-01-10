package database

import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import database.PointsDataAccessActor.{AddNegativePoints, AddPositivePoints, DeductPoints, GetTotalPoints}
import model.Company.{Company, Dannon, MillerCoors, Unilever}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import akka.testkit.{ImplicitSender, TestKit}
import model.Error.{LowBalanceException, UserDoesNotExistException}
import model.Points

import scala.concurrent.Future


class PointsDataAccessActorSpec
  extends TestKit(ActorSystem("PointsDataAccessActorSpec"))
    with FlatSpecLike
    with ImplicitSender
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with BeforeAndAfterAll {


  class FixtureParam {
    implicit val ec = system.dispatcher
    implicit val messageTimeout = Timeout(5, TimeUnit.SECONDS)
    val dataAccessActor = system.actorOf(Props(classOf[PointsDataAccessActor], ec), name = UUID.randomUUID().toString)
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "PointsDataAccessActor" should "add a new user, add points, and then deduct points" in {
    val fixture = new FixtureParam
    import fixture._

    val userId = (dataAccessActor ? PointsDataAccessActor.AddNewUser).mapTo[UUID].futureValue

    (dataAccessActor ? AddPositivePoints(userId, Points(300, OffsetDateTime.now(), Dannon))).mapTo[Unit].futureValue
    (dataAccessActor ? AddPositivePoints(userId, Points(200, OffsetDateTime.now(), Unilever))).mapTo[Unit].futureValue
    (dataAccessActor ? AddNegativePoints(userId, Points(-200, OffsetDateTime.now(), Dannon))).mapTo[Unit].futureValue
    (dataAccessActor ? AddPositivePoints(userId, Points(10000, OffsetDateTime.now(), MillerCoors))).mapTo[Unit].futureValue
    (dataAccessActor ? AddPositivePoints(userId, Points(1000, OffsetDateTime.now(), Dannon))).mapTo[Unit].futureValue

    val totals = (dataAccessActor ? GetTotalPoints(userId)).mapTo[Map[Company, Int]].futureValue
    assert(totals == Map(Dannon -> 1100, Unilever -> 200, MillerCoors -> 10000))

    val result = (dataAccessActor ? DeductPoints(userId, 5000)).mapTo[Seq[Points]].futureValue
    assert(result.length == 3)
    assert(result.filter(_.company == Dannon).head.value == -100)
    assert(result.filter(_.company == Unilever).head.value == -200)
    assert(result.filter(_.company == MillerCoors).head.value == -4700)

    val totalsAfter = (dataAccessActor ? GetTotalPoints(userId)).mapTo[Map[Company, Int]].futureValue
    assert(totalsAfter == Map(Dannon -> 1000, Unilever -> 0, MillerCoors -> 5300))
  }

  it should "fail if user does not exist" in {
    val fixture = new FixtureParam
    import fixture._

    assert((dataAccessActor ? PointsDataAccessActor.AddNewUser).mapTo[UUID].futureValue.isInstanceOf[UUID])

    assert((dataAccessActor ? AddPositivePoints(UUID.randomUUID(), Points(10, OffsetDateTime.now(), MillerCoors))).mapTo[Unit].failed.futureValue.isInstanceOf[UserDoesNotExistException])
    assert((dataAccessActor ? AddNegativePoints(UUID.randomUUID(), Points(-10, OffsetDateTime.now(), MillerCoors))).mapTo[Unit].failed.futureValue.isInstanceOf[UserDoesNotExistException])
    assert((dataAccessActor ? GetTotalPoints(UUID.randomUUID())).mapTo[Unit].failed.futureValue.isInstanceOf[UserDoesNotExistException])
    assert((dataAccessActor ? DeductPoints(UUID.randomUUID(), 5000)).mapTo[Unit].failed.futureValue.isInstanceOf[UserDoesNotExistException])
  }

  it should "add modify user 1 without modifying user 2" in {
    val fixture = new FixtureParam
    import fixture._

    val userId1 = (dataAccessActor ? PointsDataAccessActor.AddNewUser).mapTo[UUID].futureValue
    val userId2 = (dataAccessActor ? PointsDataAccessActor.AddNewUser).mapTo[UUID].futureValue

    val add1: Future[Unit] = (dataAccessActor ? AddPositivePoints(userId1, Points(10, OffsetDateTime.now(), Dannon))).mapTo[Unit]
    val add2: Future[Unit] = (dataAccessActor ? AddPositivePoints(userId2, Points(5, OffsetDateTime.now(), Unilever))).mapTo[Unit]

    val result1 = add1.map(_ => (dataAccessActor ? GetTotalPoints(userId1)).mapTo[Map[Company, Int]])
    val result2 = add2.map(_ => (dataAccessActor ? GetTotalPoints(userId2)).mapTo[Map[Company, Int]])

    for {
      totals1 <- result1
      totals2 <- result2
    } yield {
      assert(totals1 == Map(Dannon -> 10, Unilever -> 0, MillerCoors -> 0))
      assert(totals2 == Map(Dannon -> 0, Unilever -> 5, MillerCoors -> 0))
    }
  }

  it should "forward a low balance failure from the user actor" in {
    val fixture = new FixtureParam
    import fixture._

    val userId = (dataAccessActor ? PointsDataAccessActor.AddNewUser).mapTo[UUID].futureValue

    assert((dataAccessActor ? DeductPoints(userId, 5000)).mapTo[Unit].failed.futureValue.isInstanceOf[LowBalanceException])
  }
}

