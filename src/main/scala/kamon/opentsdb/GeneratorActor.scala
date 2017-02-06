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

import akka.actor.{Actor, Props}
import com.typesafe.config.Config
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot

import scala.collection.JavaConverters._


object GeneratorActor {
  def props(config: Config): Props = Props(classOf[GeneratorActor], config, new DirectDataPointSender(config.getString("direct.quorum")))
}

class GeneratorActor(val config: Config, sender : DataPointSender) extends Actor  {
   implicit protected val actorSystem = context.system
   val factory = new NameTemplateFactory(config)

   val categoryGenerators = config.getObject("subscriptions").keySet().asScala.collect {
      case "counter" => "counter" -> factory.createCounterGenerator()
      case category => category -> factory.createHistogramGenerator(s"metric.${category}")
   }.toMap

   val timestamp : (TickMetricSnapshot) => Long = config.getString("timestamp") match {
      case "seconds" => (tick : TickMetricSnapshot) => tick.to.toTimestamp.seconds
      case "milliseconds" => (tick : TickMetricSnapshot) => tick.to.millis
   }

   def receive = {
      case tick: TickMetricSnapshot =>
         generateMetricsData(tick)
   }

   protected def generateMetricsData(tick: TickMetricSnapshot): Unit = {
      val ts = timestamp(tick)
      for {
         (entity, snapshot) <- tick.metrics
         (metricKey, metricSnapshot) <- snapshot.metrics
      } {
         categoryGenerators(entity.category)(entity, metricKey, ts, metricSnapshot).foreach(sender.appendPoint)
      }
   }
}

