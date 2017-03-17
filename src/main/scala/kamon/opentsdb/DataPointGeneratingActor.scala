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

import java.time.{Instant, LocalTime, ZonedDateTime}

import akka.actor.{Actor, Props}
import akka.event.Logging
import com.typesafe.config.Config
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.util.ConfigTools.Syntax
import kamon.util.MilliTimestamp

import scala.collection.JavaConverters._
import scala.collection.mutable


object DataPointGeneratingActor {
  def props(config: Config, sender : DataPointSender): Props = Props(classOf[DataPointGeneratingActor], config, sender)
}

/**
  * Main entry point of the extension.  Converts a [[TickMetricSnapshot]] into [[DataPoint]]s and commits them to
  * OpenTSDB
  */
class DataPointGeneratingActor(config: Config, sender : DataPointSender) extends Actor {
   implicit protected val actorSystem = context.system
   def toLocalTime(timestamp : MilliTimestamp) = {
      LocalTime.from(Instant.ofEpochMilli(timestamp.millis).atZone(ZonedDateTime.now().getZone))
   }

   // Create all the DataPointGenerators
   val factory = new DataPointGeneratorFactory(config)

   val namedMetrics = config.getObject("metrics").keySet().asScala
   val metricGenerators = mutable.Map[(String, String), DataPointGenerator]()

   val categoryGenerators = config.getConfig("subscriptions").firstLevelKeys.collect {
      case category => category -> factory.createCategoryGenerator(category)
   }.toMap


   def receive = {
      case tick: TickMetricSnapshot =>
         Logging(context.system, this).debug(s"Processing tick from ${toLocalTime(tick.from)} to ${toLocalTime(tick.to)}")
         for {
            (entity, snapshot) <- tick.metrics
            (metricKey, metricSnapshot) <- snapshot.metrics
         } yield {
            val generator = metricGenerators.getOrElseUpdate((entity.category, entity.name), {
               if (namedMetrics.contains(entity.name)) {
                  factory.createMetricGenerator(entity.name, entity.category)
               } else {
                  categoryGenerators(entity.category)
               }
            })
            val ctx = MetricContext(tick, entity, metricKey, metricSnapshot)
            val points = generator(ctx)
            points.foreach(sender.appendPoint)
         }
   }
}

