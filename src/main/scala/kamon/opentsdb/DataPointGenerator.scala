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

import kamon.metric.instrument.{Counter, Histogram}

trait DataPointGenerator {
   def apply(ctx : MetricContext): Seq[DataPoint]
}

class NonIdleDecorator(generator : DataPointGenerator) extends DataPointGenerator {
   def apply(ctx : MetricContext): Seq[DataPoint] = {
      ctx.snapshot match {
         case cs : Counter.Snapshot if cs.count == 0 => Seq()
         case hs : Histogram.Snapshot if hs.numberOfMeasurements == 0 => Seq()
         case _ => generator(ctx)
      }
   }
}

class DataPointGeneratorImpl(stats : Seq[Stat],
                             nameGenerator : MetricNameGenerator,
                             tagGenerator : MetricTagGenerator,
                             timeGenerator : TimestampGenerator
                            ) extends DataPointGenerator {
   def apply(ctx : MetricContext): Seq[DataPoint] = {
      stats.flatMap { stat =>
         val name = nameGenerator(ctx, stat)
         val tags = tagGenerator(ctx, stat)
         val pf = stat(ctx)
         pf.lift(ctx.snapshot).map { value =>
            DataPoint(name, tags, timeGenerator(ctx, stat), value)
         }
      }
   }
}







