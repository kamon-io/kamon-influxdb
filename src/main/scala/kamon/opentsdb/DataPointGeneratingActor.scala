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
import akka.event.Logging
import com.typesafe.config.Config
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.util.ConfigTools.Syntax
import scala.collection.JavaConverters._


object DataPointGeneratingActor {
  def props(config: Config, sender : DataPointSender): Props = Props(classOf[DataPointGeneratingActor], config, sender)
}

/**
  * Main entry point of the extension.  Converts a [[TickMetricSnapshot]] into [[DataPoint]]s and commits them to
  * OpenTSDB
  */
class DataPointGeneratingActor(config: Config, sender : DataPointSender) extends Actor {
   implicit protected val actorSystem = context.system

   // Create all the DataPointGenerators
   val factory = new DataPointGeneratorFactory(config)

   val metricGenerators = config.getObject("metrics").keySet().asScala.collect {
      case name => name -> factory.createDataPointGenerator(s"metrics.${name}")
   }.toMap

   val categoryGenerators = config.getConfig("subscriptions").firstLevelKeys.collect {
      case category => category -> factory.createDataPointGenerator(s"category.${category}")
   }.toMap


   def receive = {
      case tick: TickMetricSnapshot =>
         Logging(context.system, this).info("processing tick")
         for {
            (entity, snapshot) <- tick.metrics
            (metricKey, metricSnapshot) <- snapshot.metrics
         } yield {
            val ctx = MetricContext(tick, entity, metricKey, metricSnapshot)
            val generator = metricGenerators.getOrElse(entity.name, categoryGenerators(entity.category))
            val points = generator(ctx)
            points.foreach(sender.appendPoint)
         }
   }
}

