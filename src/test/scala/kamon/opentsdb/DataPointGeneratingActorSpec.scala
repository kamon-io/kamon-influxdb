/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.opentsdb

import com.typesafe.config.{Config, ConfigFactory}
import kamon.metric.MetricsModuleImpl
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.CollectionContext
import kamon.testkit.BaseKamonSpec
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar

class DataPointGeneratingActorSpec extends BaseKamonSpec("generator-spec") with MockitoSugar {

   override lazy val config = ConfigFactory.parseString("kamon.opentsdb.name.parts.application.value = testApp").withFallback(ConfigFactory.load()).resolve()

   class Fixture(fixtureConfig: Config) extends SenderFixture {
      val metrics = new MetricsModuleImpl(config)

      def setup : (DataPointSender, TickMetricSnapshot) = {
         val sender = mock[DataPointSender]
         val metricsSender = system.actorOf(DataPointGeneratingActor.props(fixtureConfig, sender))
         val snapshots = metrics.collectSnapshots(CollectionContext(500))
         val tick = TickMetricSnapshot(from, to, snapshots)
         metricsSender ! tick
         (sender, tick)
      }
  }


  "the OpenTSDBSender" should {
    "generate metrics for counters" in new Fixture(config.getConfig("kamon.opentsdb")) {
       metrics.counter("foo").increment()
       val (sender, tick) = setup
       Thread.sleep(1000)
       Mockito.verify(sender).appendPoint(DataPoint("counter/foo/count", Map("host" -> "KenPC"), tick.to.toTimestamp.seconds, 1))
       Mockito.verify(sender).appendPoint(DataPoint("counter/foo/rate", Map("host" -> "KenPC"), tick.to.toTimestamp.seconds, 1))
    }

     "generate metrics for histogram" in new Fixture(config.getConfig("kamon.opentsdb")) {
        metrics.histogram("foo").record(10)
        metrics.histogram("foo").record(15)
        metrics.histogram("foo").record(30)
        val (sender, tick) = setup
        Thread.sleep(1000)
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/count", Map("host" -> "KenPC"), tick.to.toTimestamp.seconds, 3))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/rate", Map("host" -> "KenPC"), tick.to.toTimestamp.seconds, 3))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/min", Map("host" -> "KenPC"), tick.to.toTimestamp.seconds, 10))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/max", Map("host" -> "KenPC"), tick.to.toTimestamp.seconds, 30))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/median", Map("host" -> "KenPC"), tick.to.toTimestamp.seconds, 15))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/mean", Map("host" -> "KenPC"), tick.to.toTimestamp.seconds, 18))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/90", Map("host" -> "KenPC"), tick.to.toTimestamp.seconds, 30))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/99", Map("host" -> "KenPC"), tick.to.toTimestamp.seconds, 30))
     }

//    "connect to the correct database" in new Fixture(influxDBConfig) {
//      val testrecorder = buildRecorder("user/kamon")
//      val http = setup(Map(testEntity -> testrecorder.collect(collectionContext)))
//      val request = getHttpRequest(http)
//
//      val query = getQueryParameters(request.uri)
//
//      query("db") shouldBe influxDBConfig.getString("database")
//    }
//
//    "use authentication and retention policy are defined" in new Fixture(configWithAuthAndRetention) {
//      val testrecorder = buildRecorder("user/kamon")
//      val http = setup(Map(testEntity -> testrecorder.collect(collectionContext)))
//      val request = getHttpRequest(http)
//
//      val query = getQueryParameters(request.uri)
//
//      query("u") shouldBe configWithAuthAndRetention.getString("authentication.user")
//      query("p") shouldBe configWithAuthAndRetention.getString("authentication.password")
//      query("rp") shouldBe configWithAuthAndRetention.getString("retention-policy")
//    }
//
//    "send counters in a correct format" in new Fixture(influxDBConfig) {
//      val testrecorder = buildRecorder("user/kamon")
//      (0 to 2).foreach(_ ⇒ testrecorder.counter.increment())
//
//      val http = setup(Map(testEntity -> testrecorder.collect(collectionContext)))
//      val expectedMessage = s"kamon-counters,category=test,entity=user-kamon,hostname=$hostName,metric=metric-two value=3 ${from.millis * 1000000}"
//
//      val request = getHttpRequest(http)
//      val requestData = request.payload.split("\n")
//
//      requestData should contain(expectedMessage)
//    }
//
//    "send histograms in a correct format" in new Fixture(influxDBConfig) {
//      val testRecorder = buildRecorder("user/kamon")
//
//      testRecorder.histogramOne.record(10L)
//      testRecorder.histogramOne.record(5L)
//
//      val http = setup(Map(testEntity -> testRecorder.collect(collectionContext)))
//      val expectedMessage = s"kamon-timers,category=test,entity=user-kamon,hostname=$hostName,metric=metric-one mean=7.5,lower=5,upper=10,p70.5=10,p50=5 ${from.millis * 1000000}"
//
//      val request = getHttpRequest(http)
//      val requestData = request.payload.split("\n")
//
//      requestData should contain(expectedMessage)
//    }
  }
}

