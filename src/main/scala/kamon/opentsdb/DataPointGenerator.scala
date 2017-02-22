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

class DataPointGenerator(stats : Seq[Stat],
                         nameGenerator : MetricNameGenerator,
                         tagGenerator : MetricTagGenerator,
                         timeGenerator : TimestampGenerator
                        ) {
   def apply(ctx : MetricContext): Seq[DataPoint] = {
      stats.flatMap { stat =>
         val name = nameGenerator(ctx, stat)
         val tags = tagGenerator(ctx, stat)
         val pf = stat(ctx)
         pf.lift(ctx.snapshot).map { value => DataPoint(name, tags, timeGenerator(ctx, stat), value) }
      }
   }
}


class MetricNameGenerator(nameGenerators : Seq[NormalizedRule], separator: String) {
   def apply(ctx : MetricContext, stat : Stat) : String = {
      nameGenerators.map(_.apply(ctx, stat)).filter { _.nonEmpty }.mkString(separator)
   }
}

class MetricTagGenerator(tagGenerators : Map[String, NormalizedRule]) {
   def apply(ctx : MetricContext, stat : Stat) = {
      ctx.entity.tags ++ tagGenerators.map { case (tagName, generator) => tagName -> generator(ctx, stat)}.filter( _._2.nonEmpty )
   }
}


