package co.adhoclabs.akka.http.contrib.throttle

import java.time.Instant

import com.typesafe.scalalogging.StrictLogging
import scalacache._
import scalacache.caffeine._

import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future }

class CaffeineCacheMetricStore(namespace: String = "")(implicit ec: ExecutionContext)
    extends MetricStore
    with StrictLogging {

  import scalacache.modes.scalaFuture._

  val cache = CaffeineCache[Long]

  override def keyForEndpoint(throttleEndpoint: ThrottleEndpoint, url: String): String =
    s"$namespace${super.keyForEndpoint(throttleEndpoint, url)}"

  /**
    * get should return the current value for current window or zero. Meaning...
    * if there is no value it should return 0
    * if there is value and window changed it should return 0
    * if there is value and current window didn't change it should return that value
    *
    * @return
    */
  override def get(throttleEndpoint: ThrottleEndpoint, url: String): Future[Long] = {
    val k = keyForEndpoint(throttleEndpoint, url)
    logger.debug(s"retrieving cached value for $k")
    cache.doGet(k).map(_.getOrElse(0))
  }

  /**
    * Same rules apply here as in get. incr should set the value to current value + 1 for current window or 1.
    *
    * @param throttleEndpoint
    */
  override def incr(throttleEndpoint: ThrottleEndpoint, url: String): Future[Unit] = {

    val key             = keyForEndpoint(throttleEndpoint, url)
    val throttleDetails = throttleEndpoint.throttleDetails

    val expires =
      Instant.now().plusMillis(throttleDetails.window.toMillis)
    val entry = cache.underlying.get(key, {
      case _: String =>
        Entry[Long](0, Some(expires))
    })
    val count = entry.value

    logger.debug(s"key $key: $entry")
    val newCount = count + 1
    logger.debug(s"increasing count for $key to $newCount")
    val ttl = throttleEndpoint.throttleDetails.window
    cache.doPut(key, newCount, Some(ttl)).flatMap(_ => Future.unit)
//    }

//    throttleDetails.throttlePeriod
//      .filter { p ⇒
//        logger.debug(
//          s"checking access nr $count against limit of ${throttleDetails.allowedCalls} within $p")
//        count >= throttleDetails.allowedCalls
//      } //
    //  cache.pExpire(key, p.toMillis)
//      .map { p: Duration ⇒
//        logger.debug(s"exceeded throttle limit, resetting count")
//        cache.doPut(key, 1, Some(p))
//      }
//      .getOrElse {
//        val newCount = count + 1
//        logger.debug(s"no limit exceeded, increasing count to $newCount")
//        cache.doPut(key, newCount, None)
//        Future.successful(false)
//      }
//      .map(_ => ())
  }

  override def set(throttleEndpoint: ThrottleEndpoint, url: String, count: Long): Future[Unit] = {
    val k   = keyForEndpoint(throttleEndpoint, url)
    val ttl = throttleEndpoint.throttleDetails.window
    logger.debug(s"setting $k to $count for $ttl")
    cache.doPut(k, count, Some(ttl)).flatMap(_ => Future.unit)
  }

}
