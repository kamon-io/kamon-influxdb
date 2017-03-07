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

import java.lang.management.ManagementFactory

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

   val hostname = ManagementFactory.getRuntimeMXBean.getName.split('@')(1)
   
   
  "the OpenTSDBSender" should {
    "generate metrics for counters" in new Fixture(config.getConfig("kamon.opentsdb")) {
       metrics.counter("foo").increment()
       val (sender, tick) = setup
       Thread.sleep(1000)
       Mockito.verify(sender).appendPoint(DataPoint("counter/foo/count", Map("host" -> hostname), tick.to.toTimestamp.seconds, 1))
       Mockito.verify(sender).appendPoint(DataPoint("counter/foo/rate", Map("host" -> hostname), tick.to.toTimestamp.seconds, 1))
    }

     "generate metrics for histogram" in new Fixture(config.getConfig("kamon.opentsdb")) {
        metrics.histogram("foo").record(10)
        metrics.histogram("foo").record(15)
        metrics.histogram("foo").record(30)
        val (sender, tick) = setup
        Thread.sleep(1000)
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/count", Map("host" -> hostname), tick.to.toTimestamp.seconds, 3))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/rate", Map("host" -> hostname), tick.to.toTimestamp.seconds, 3))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/min", Map("host" -> hostname), tick.to.toTimestamp.seconds, 10))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/max", Map("host" -> hostname), tick.to.toTimestamp.seconds, 30))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/median", Map("host" -> hostname), tick.to.toTimestamp.seconds, 15))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/mean", Map("host" -> hostname), tick.to.toTimestamp.seconds, 18))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/90", Map("host" -> hostname), tick.to.toTimestamp.seconds, 30))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/99", Map("host" -> hostname), tick.to.toTimestamp.seconds, 30))
     }
  }
}

