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

import com.typesafe.config.Config
import kamon.metric.MetricsModuleImpl
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.CollectionContext
import kamon.testkit.BaseKamonSpec
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar

class MetricOverrideSpec extends BaseKamonSpec("generator-spec") with MockitoSugar {

   //override lazy val config = ConfigValueFactory.fromAnyRef("testApp").atPath("kamon.opentsdb.rules.application.value").withFallback(super.config).resolve()

   class Fixture(fixtureConfig: Config) extends SenderFixture {
      val metrics = new MetricsModuleImpl(fixtureConfig)

      def setup : (DataPointSender, TickMetricSnapshot) = {
         val sender = mock[DataPointSender]
         val metricsSender = system.actorOf(DataPointGeneratingActor.props(fixtureConfig.getConfig("kamon.opentsdb"), sender))
         val snapshots = metrics.collectSnapshots(CollectionContext(500))
         val tick = TickMetricSnapshot(from, to, snapshots)
         metricsSender ! tick
         (sender, tick)
      }
   }

   val defaultTags =  Map("host" -> "testHost", "application" -> "testApp")

   "metric specific values should " should {
      "override the name rules" in new Fixture(config) {
         metrics.counter("test.name.override")
         val (sender, tick) = setup
         Thread.sleep(1000)
         Mockito.verify(sender).appendPoint(DataPoint("count/counter/test.name.override/counter/testHost/testApp", defaultTags, tick.to.toTimestamp.seconds, 0))
      }

      "override the tag rules" in new Fixture(config) {
         // Should use the name from the category
         metrics.counter("test.tag.override")

         // Should use the default name
         buildRecorder("test.tag.override")
         val (sender, tick) = setup
         Thread.sleep(1000)
         Mockito.verify(sender).appendPoint(DataPoint("counter/test.tag.override/count", Map("category" -> "counter"), tick.to.toTimestamp.seconds, 0))
         Mockito.verify(sender).appendPoint(DataPoint("testEntity/test.tag.override/metric-one/count", Map("category" -> "testEntity"), tick.to.toTimestamp.seconds, 0))
         Mockito.verify(sender).appendPoint(DataPoint("testEntity/test.tag.override/metric-two/count", Map("category" -> "testEntity"), tick.to.toTimestamp.seconds, 0))
      }

      "override the stats rules" in new Fixture(config) {
         (1 to 100).foreach { i => metrics.histogram("test.stat.override").record(i)}
         val (sender, tick) = setup
         Thread.sleep(1000)
         Mockito.verify(sender).appendPoint(DataPoint("histogram/test.stat.override/30", defaultTags, tick.to.toTimestamp.seconds, 30))
         Mockito.verify(sender).appendPoint(DataPoint("histogram/test.stat.override/60", defaultTags, tick.to.toTimestamp.seconds, 60))
         Mockito.verify(sender).appendPoint(DataPoint("histogram/test.stat.override/99.99", defaultTags, tick.to.toTimestamp.seconds, 100))
         Mockito.verifyNoMoreInteractions(sender)
      }

      "override the timestamp" in new Fixture(config) {
         metrics.counter("test.timestamp.override")
         val (sender, tick) = setup
         Thread.sleep(1000)
         Mockito.verify(sender).appendPoint(DataPoint("counter/test.timestamp.override/count", defaultTags, tick.to.millis, 0))
      }

      "override filter idle" in new Fixture(config) {
         metrics.counter("test.filter.override")
         val (sender, tick) = setup
         Thread.sleep(1000)
         Mockito.verifyZeroInteractions(sender)
      }
   }
}

