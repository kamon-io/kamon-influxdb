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

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions, ConfigValueFactory}
import kamon.Kamon
import kamon.metric.MetricsModuleImpl
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.CollectionContext
import kamon.testkit.BaseKamonSpec
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar

class StatGenerationSpec extends BaseKamonSpec("generator-spec") with MockitoSugar {

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
   
  "stats should be generated" should {
    "for counters" in new Fixture(config) {
       metrics.counter("foo").increment()
       val (sender, tick) = setup
       Thread.sleep(1000)
       Mockito.verify(sender).appendPoint(DataPoint("counter/foo/count", defaultTags, tick.to.toTimestamp.seconds, 1))
       Mockito.verify(sender).appendPoint(DataPoint("counter/foo/rate", defaultTags, tick.to.toTimestamp.seconds, 1))
       Mockito.verifyNoMoreInteractions(sender)
    }

     "for histogram" in new Fixture(config) {
        (1 to 100).foreach { i => metrics.histogram("foo").record(i)}
        val (sender, tick) = setup
        Thread.sleep(1000)
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/count", defaultTags, tick.to.toTimestamp.seconds, 100))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/rate", defaultTags, tick.to.toTimestamp.seconds, 100))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/min", defaultTags, tick.to.toTimestamp.seconds, 1))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/max", defaultTags, tick.to.toTimestamp.seconds, 100))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/median", defaultTags, tick.to.toTimestamp.seconds, 50))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/mean", defaultTags, tick.to.toTimestamp.seconds,50))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/90", defaultTags, tick.to.toTimestamp.seconds, 90))
        Mockito.verify(sender).appendPoint(DataPoint("histogram/foo/99", defaultTags, tick.to.toTimestamp.seconds, 99))
        Mockito.verifyNoMoreInteractions(sender)
     }

     // This assumes that refreshValues gets called once.
     "for min-max-counter" in new Fixture(config) {
        (1 to 50).foreach { i => metrics.minMaxCounter("foo").increment()}
        (1 to 10).foreach { i => metrics.minMaxCounter("foo").decrement()}
        val (sender, tick) = setup
        Thread.sleep(1000)
        Mockito.verify(sender).appendPoint(DataPoint("min-max-counter/foo/min", defaultTags, tick.to.toTimestamp.seconds, 0))
        Mockito.verify(sender).appendPoint(DataPoint("min-max-counter/foo/max", defaultTags, tick.to.toTimestamp.seconds, 50))
        Mockito.verify(sender).appendPoint(DataPoint("min-max-counter/foo/median", defaultTags, tick.to.toTimestamp.seconds, 40))
        Mockito.verify(sender).appendPoint(DataPoint("min-max-counter/foo/mean", defaultTags, tick.to.toTimestamp.seconds,30))
        Mockito.verify(sender).appendPoint(DataPoint("min-max-counter/foo/90", defaultTags, tick.to.toTimestamp.seconds, 50))
        Mockito.verify(sender).appendPoint(DataPoint("min-max-counter/foo/99", defaultTags, tick.to.toTimestamp.seconds, 50))
        Mockito.verifyNoMoreInteractions(sender)
     }


     "for gauge" in new Fixture(config) {
        val values = 1L to 100L
        val gauge = metrics.gauge("foo")(values.iterator.next _)
        values.foreach { i => gauge.refreshValue() }

        val (sender, tick) = setup
        Thread.sleep(1000)
        Mockito.verify(sender).appendPoint(DataPoint("gauge/foo/count", defaultTags, tick.to.toTimestamp.seconds, 100))
        Mockito.verify(sender).appendPoint(DataPoint("gauge/foo/rate", defaultTags, tick.to.toTimestamp.seconds, 100))
        Mockito.verify(sender).appendPoint(DataPoint("gauge/foo/min", defaultTags, tick.to.toTimestamp.seconds, 1))
        Mockito.verify(sender).appendPoint(DataPoint("gauge/foo/max", defaultTags, tick.to.toTimestamp.seconds, 100))
        Mockito.verify(sender).appendPoint(DataPoint("gauge/foo/median", defaultTags, tick.to.toTimestamp.seconds, 50))
        Mockito.verify(sender).appendPoint(DataPoint("gauge/foo/mean", defaultTags, tick.to.toTimestamp.seconds,50))
        Mockito.verify(sender).appendPoint(DataPoint("gauge/foo/90", defaultTags, tick.to.toTimestamp.seconds, 90))
        Mockito.verify(sender).appendPoint(DataPoint("gauge/foo/99", defaultTags, tick.to.toTimestamp.seconds, 99))
        Mockito.verifyNoMoreInteractions(sender)
     }

     "for an entity" in new Fixture(config) {
        val entity = buildRecorder("foo")

        (1L to 100L).foreach{ i =>
           entity.counter.increment()
           entity.histogramOne.record(i)
        }

        val (sender, tick) = setup
        Thread.sleep(1000)
        Mockito.verify(sender).appendPoint(DataPoint("testEntity/foo/metric-one/count", defaultTags, tick.to.toTimestamp.seconds, 100))
        Mockito.verify(sender).appendPoint(DataPoint("testEntity/foo/metric-one/rate", defaultTags, tick.to.toTimestamp.seconds, 100))
        Mockito.verify(sender).appendPoint(DataPoint("testEntity/foo/metric-one/min", defaultTags, tick.to.toTimestamp.seconds, 1))
        Mockito.verify(sender).appendPoint(DataPoint("testEntity/foo/metric-one/max", defaultTags, tick.to.toTimestamp.seconds, 100))
        Mockito.verify(sender).appendPoint(DataPoint("testEntity/foo/metric-one/median", defaultTags, tick.to.toTimestamp.seconds, 50))
        Mockito.verify(sender).appendPoint(DataPoint("testEntity/foo/metric-one/mean", defaultTags, tick.to.toTimestamp.seconds,50))
        Mockito.verify(sender).appendPoint(DataPoint("testEntity/foo/metric-one/90", defaultTags, tick.to.toTimestamp.seconds, 90))
        Mockito.verify(sender).appendPoint(DataPoint("testEntity/foo/metric-one/99", defaultTags, tick.to.toTimestamp.seconds, 99))
        Mockito.verify(sender).appendPoint(DataPoint("testEntity/foo/metric-two/count", defaultTags, tick.to.toTimestamp.seconds, 100))
        Mockito.verify(sender).appendPoint(DataPoint("testEntity/foo/metric-two/rate", defaultTags, tick.to.toTimestamp.seconds, 100))
        Mockito.verifyNoMoreInteractions(sender)
     }
  }
}

