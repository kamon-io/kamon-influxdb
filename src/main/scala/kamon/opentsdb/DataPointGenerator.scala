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

import kamon.metric.instrument.{Counter, Histogram, InstrumentSnapshot}
import kamon.metric.{Entity, MetricKey}
import kamon.opentsdb.names.NormalizedRule
import kamon.opentsdb.stats.Stat

trait DataPointGenerator {
   def apply(entity: Entity, metricKey: MetricKey, timestamp : Long, snapshot : InstrumentSnapshot): Seq[DataPoint]
}

class CounterDataPointGenerator(nameGenerator : MetricNameGenerator, tagGenerator: MetricTagGenerator) extends DataPointGenerator {
   def apply(entity: Entity, metricKey: MetricKey, timestamp : Long, snapshot : InstrumentSnapshot): Seq[DataPoint] = {
      snapshot match {
         case cs : Counter.Snapshot =>
            Seq(DataPoint(nameGenerator(entity, metricKey), tagGenerator(entity, metricKey), timestamp, cs.count))
      }
   }
}

class HistogramDataPointGenerator(includeCount : Boolean,
                                  statTag : String,
                                  stats : Seq[Stat],
                                  nameGenerator : MetricNameGenerator,
                                  tagGenerator: MetricTagGenerator
                                 ) extends DataPointGenerator {
   def apply(entity: Entity, metricKey: MetricKey, timestamp: Long, snapshot: InstrumentSnapshot): Seq[DataPoint] = {
      snapshot match {
         case hs: Histogram.Snapshot =>
            val name = nameGenerator(entity, metricKey)
            val tags = tagGenerator(entity, metricKey)

            val counterStat = if (includeCount) {
               Seq(DataPoint(nameGenerator(entity, metricKey, "counter"), tags, timestamp, hs.numberOfMeasurements))
            } else {
               Nil
            }

            counterStat ++ stats.map { stat =>
               DataPoint(name, tags + (statTag -> stat.name), timestamp, stat(hs))
            }
      }
   }
}


class MetricNameGenerator(nameGenerators : Seq[NormalizedRule], separator: String) {
   def apply(entity: Entity, metricKey: MetricKey) : String = {
      nameGenerators.map(_.apply(entity, metricKey)).filter { _.nonEmpty }.mkString(separator)
   }

   def apply(entity: Entity, metricKey: MetricKey, suffix : String) : String = {
      s"${apply(entity, metricKey)}$separator$suffix"
   }
}

class MetricTagGenerator(tagGenerators : Map[String, NormalizedRule]) {
   def apply(entity: Entity, metricKey: MetricKey) = {
      tagGenerators.map { case (tagName, generator) => tagName -> generator(entity, metricKey)}.filter( _._2.nonEmpty )
   }
}


