package controllers

import java.time.OffsetDateTime
import java.util.UUID

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import database.PointsDataAccessActor.{AddPositivePoints, DeductPoints}
import model.Company.Dannon
import model.Error.{LowBalanceException, UserDoesNotExistException}
import model.Points
import model.http.{AddNewUserResponse, AddPointsRequest, DeductPointsRequest, TotalPoints}
import model.http.GetTotalPointsResponse.GetTotalPointsResponse
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, MustMatchers, OptionValues}
import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.http.HttpEntity
import play.api.libs.json.Json
import play.api.test._
import play.api.test.Helpers._

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 *
 * For more information, see https://www.playframework.com/documentation/latest/ScalaTestingWithScalaTest
 */
class PointsTrackerControllerSpec
  extends TestKit(ActorSystem("PointsTrackerControllerSpec"))
    with FlatSpecLike
    with MustMatchers
    with OptionValues
    with ScalaFutures
    with WsScalaTestClient
    with BeforeAndAfterAll {

  class FixtureParam(mockDataActor: ActorRef) {
    val userId = UUID.randomUUID()
    implicit val ec = system.dispatcher
    val controller = new PointsTrackerControllerImpl(mockDataActor, stubControllerComponents())
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "addPoints" should "add positive points and return successfully" in {
    val mockDataActor = TestActorRef(new Actor {
      override def receive: Receive = {
        case msg => sender ! ()
      }
    })
    val fixture = new FixtureParam(mockDataActor)
    import fixture._
    val action = controller.addPoints(userId)
    val request = FakeRequest(POST, s"/add/points/$userId").withBody(Json.toJson(AddPointsRequest(userId, 10, Dannon)))
    val result = call(action, request).futureValue

    assert(result.header.status == 200)
  }

  it should "return 400 if the user does not exist" in {
    val mockDataActor = TestActorRef(new Actor {
      override def receive: Receive = {
        case msg => sender ! akka.actor.Status.Failure(new UserDoesNotExistException(s"User does not exist"))
      }
    })
    val fixture = new FixtureParam(mockDataActor)
    import fixture._
    val action = controller.addPoints(userId)
    val request = FakeRequest(POST, s"/add/points/$userId").withBody(Json.toJson(AddPointsRequest(userId, 10, Dannon)))
    val result = call(action, request).futureValue

    assert(result.header.status == 400)
  }

  it should "return 400 for badly formatted request" in {
    val mockDataActor = TestActorRef(new Actor {
      override def receive: Receive = {
        case msg => sender ! ()
      }
    })
    val fixture = new FixtureParam(mockDataActor)
    import fixture._
    val action = controller.addPoints(userId)
    val request = FakeRequest(POST, s"/add/points/$userId").withBody(Json.parse(s"{}"))
    val result = call(action, request).futureValue

    assert(result.header.status == 400)
  }

  it should "return 400 if the points to add is zero" in {
    val mockDataActor = TestActorRef(new Actor {
      override def receive: Receive = {
        case msg => sender ! ()
      }
    })
    val fixture = new FixtureParam(mockDataActor)
    import fixture._
    val action = controller.addPoints(userId)
    val request = FakeRequest(POST, s"/add/points/$userId").withBody(Json.toJson(AddPointsRequest(userId, 0, Dannon)))
    val result = call(action, request).futureValue

    assert(result.header.status == 400)
  }

  it should "return 500 for any other exception from the data layer" in {
    val mockDataActor = TestActorRef(new Actor {
      override def receive: Receive = {
        case msg => sender ! akka.actor.Status.Failure(new Exception("test"))
      }
    })
    val fixture = new FixtureParam(mockDataActor)
    import fixture._
    val action = controller.addPoints(userId)
    val request = FakeRequest(POST, s"/add/points/$userId").withBody(Json.toJson(AddPointsRequest(userId, 10, Dannon)))
    val result = call(action, request).futureValue

    assert(result.header.status == 500)
  }

  "getTotalPoints" should "return 200 and the total points" in {
    val mockDataActor = TestActorRef(new Actor {
      override def receive: Receive = {
        case msg => sender ! Map(Dannon -> 10)
      }
    })
    val fixture = new FixtureParam(mockDataActor)
    import fixture._
    val action = controller.getTotalPoints(userId)
    val request = FakeRequest(GET, s"/total/points/$userId")
    val result = call(action, request)

    status(result) mustBe 200
    assert(Json.parse(contentAsString(result)).validate[GetTotalPointsResponse].get == Seq(TotalPoints(Dannon, 10)))
  }

  it should "return 500 for any other exception from the data layer" in {
    val mockDataActor = TestActorRef(new Actor {
      override def receive: Receive = {
        case msg => sender ! akka.actor.Status.Failure(new Exception("test"))
      }
    })
    val fixture = new FixtureParam(mockDataActor)
    import fixture._
    val action = controller.getTotalPoints(userId)
    val request = FakeRequest(GET, s"/total/points/$userId")
    val result = call(action, request).futureValue

    assert(result.header.status == 500)
  }

  "deductPoints" should "deduct points and return successfully" in {
    val expectedResponse = Seq(Points(-10, OffsetDateTime.now(), Dannon))
    val mockDataActor = TestActorRef(new Actor {
      override def receive: Receive = {
        case msg => sender ! expectedResponse
      }
    })
    val fixture = new FixtureParam(mockDataActor)
    import fixture._
    val action = controller.deductPoints(userId)
    val request = FakeRequest(POST, s"/deduct/points/$userId").withBody(Json.toJson(DeductPointsRequest(userId, 10)))
    val result = call(action, request)

    status(result) mustBe 200
    assert(Json.parse(contentAsString(result)).validate[Seq[Points]].get == expectedResponse)
  }

  it should "return 400 if the user does not exist" in {
    val mockDataActor = TestActorRef(new Actor {
      override def receive: Receive = {
        case msg => sender ! akka.actor.Status.Failure(new UserDoesNotExistException(s"User does not exist"))
      }
    })
    val fixture = new FixtureParam(mockDataActor)
    import fixture._
    val action = controller.deductPoints(userId)
    val request = FakeRequest(POST, s"/deduct/points/$userId").withBody(Json.toJson(DeductPointsRequest(userId, 10)))
    val result = call(action, request).futureValue

    assert(result.header.status == 400)
  }

  it should "return 400 if the user does not have enough points" in {
    val mockDataActor = TestActorRef(new Actor {
      override def receive: Receive = {
        case msg => sender ! akka.actor.Status.Failure(new LowBalanceException("not enough"))
      }
    })
    val fixture = new FixtureParam(mockDataActor)
    import fixture._
    val action = controller.deductPoints(userId)
    val request = FakeRequest(POST, s"/deduct/points/$userId").withBody(Json.toJson(DeductPointsRequest(userId, 10)))
    val result = call(action, request).futureValue

    assert(result.header.status == 400)
  }

  it should "return 400 for badly formatted request" in {
    val mockDataActor = TestActorRef(new Actor {
      override def receive: Receive = {
        case msg => sender ! ()
      }
    })
    val fixture = new FixtureParam(mockDataActor)
    import fixture._
    val action = controller.deductPoints(userId)
    val request = FakeRequest(POST, s"/deduct/points/$userId").withBody(Json.parse(s"{}"))
    val result = call(action, request).futureValue

    assert(result.header.status == 400)
  }

  it should "return 400 if the points to deduct is not positive" in {
    val mockDataActor = TestActorRef(new Actor {
      override def receive: Receive = {
        case msg => sender ! ()
      }
    })
    val fixture = new FixtureParam(mockDataActor)
    import fixture._
    val action = controller.deductPoints(userId)
    val request = FakeRequest(POST, s"/deduct/points/$userId").withBody(Json.toJson(DeductPointsRequest(userId, -10)))
    val result = call(action, request).futureValue

    assert(result.header.status == 400)
  }

  it should "return 500 for any other exception from the data layer" in {
    val mockDataActor = TestActorRef(new Actor {
      override def receive: Receive = {
        case msg => sender ! akka.actor.Status.Failure(new Exception("test"))
      }
    })
    val fixture = new FixtureParam(mockDataActor)
    import fixture._
    val action = controller.deductPoints(userId)
    val request = FakeRequest(POST, s"/deduct/points/$userId").withBody(Json.toJson(DeductPointsRequest(userId, 10)))
    val result = call(action, request).futureValue

    assert(result.header.status == 500)
  }

  "addNewUser" should "add new user and return 200" in {
    val userId = UUID.randomUUID()
    val mockDataActor = TestActorRef(new Actor {
      override def receive: Receive = {
        case msg => sender ! userId
      }
    })
    val fixture = new FixtureParam(mockDataActor)
    import fixture._
    val action = controller.addNewUser()
    val request = FakeRequest(POST, s"/add/new/user")
    val result = call(action, request)

    status(result) mustBe 200
    assert(Json.parse(contentAsString(result)).validate[AddNewUserResponse].get.userId == userId)
  }

  it should "return 500 for any other exception from the data layer" in {
    val mockDataActor = TestActorRef(new Actor {
      override def receive: Receive = {
        case msg => sender ! akka.actor.Status.Failure(new Exception("test"))
      }
    })
    val fixture = new FixtureParam(mockDataActor)
    import fixture._
    val action = controller.addNewUser()
    val request = FakeRequest(POST, s"/add/new/user")
    val result = call(action, request).futureValue

    assert(result.header.status == 500)
  }
}
