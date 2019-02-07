package co.adhoclabs.akka.http.contrib.throttle

import akka.http.scaladsl.model.{ HttpMethods, HttpRequest }
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpecLike }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

/**
  * Created by yeghishe on 6/13/16.
  */
class CaffeineMetricThrottleSettingsTest
    extends WordSpecLike
    with Matchers
    with ScalaFutures
    with MockFactory {
  import scala.concurrent.ExecutionContext.Implicits.global

  private case class TestEndpoint(name: String) extends Endpoint {
    override def matches(request: HttpRequest)(implicit ec: ExecutionContext): Future[Boolean] =
      Future(request.uri.path.toString().contains(name))(ec)
    override def getIdentifier(url: String): String = url
  }

  private val throttleDetails = ThrottleDetails(1 second, 2)

  private val namespace   = "test:"
  private val metricStore = new CaffeineCacheMetricStore(namespace)
  private val endpoint    = ThrottleEndpoint(TestEndpoint("test"), throttleDetails)
  private val metricThrottleSettings = new MetricThrottleSettings {
    implicit override val executor: ExecutionContext =
      scala.concurrent.ExecutionContext.Implicits.global
    override val store: MetricStore                = metricStore
    override val endpoints: List[ThrottleEndpoint] = List(endpoint)
  }

  "MetricThrottleSettingsTest" when {
    "shouldThrottle" should {
      "return false if no matching endpoint is found" in {
        val request = HttpRequest(method = HttpMethods.GET, uri = "/foo")

        metricThrottleSettings.shouldThrottle(request).futureValue should be(false)
      }

      "return false if matching endpoint is found and metric store has count lower than max count" in {
        val url     = "/test"
        val request = HttpRequest(method = HttpMethods.GET, uri = url)
        metricThrottleSettings.shouldThrottle(request).futureValue should be(false)
      }

      "return true if matching endpoint is found and metric store has count higher or equal to max count" in {
        val url     = "/test"
        val request = HttpRequest(method = HttpMethods.GET, uri = url)

        metricThrottleSettings.shouldThrottle(request).futureValue should be(false)
        metricStore.incr(endpoint, request.uri.path.toString())
        metricThrottleSettings.shouldThrottle(request).futureValue should be(false)
        metricStore.incr(endpoint, request.uri.path.toString())
        metricThrottleSettings.shouldThrottle(request).futureValue should be(true)
        metricStore.incr(endpoint, request.uri.path.toString())
        metricThrottleSettings.shouldThrottle(request).futureValue should be(true)
        Thread.sleep(1000)
        metricThrottleSettings.shouldThrottle(request).futureValue should be(false)
      }
    }

    "onExecute" should {
      "do nothing if no matching endpoint is found" in {
        val request = HttpRequest(method = HttpMethods.GET, uri = "/foo")

        metricThrottleSettings.onExecute(request).futureValue should be(())
      }

      "call metric store to increment the metric if matching endpoint is found" in {
        val url     = "/test"
        val request = HttpRequest(method = HttpMethods.GET, uri = url)
        metricThrottleSettings.onExecute(request).futureValue should be(())
      }
    }

    "fromConfig" should {
      "create ConfigMetricThrottleSettings" in {
        MetricThrottleSettings.fromConfig shouldBe a[ConfigMetricThrottleSettings]
      }
    }
  }
}
