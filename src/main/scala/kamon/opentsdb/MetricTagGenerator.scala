package kamon.opentsdb

/**
  * Generates the OpenTSDB tags.  Always includes the entity's tags.
  */
class MetricTagGenerator(tagGenerators : Map[String, NormalizedRule]) {
   def apply(ctx : MetricContext, stat : Stat) = {
      ctx.entity.tags ++ tagGenerators.map { case (tagName, generator) => tagName -> generator(ctx, stat)}.filter( _._2.nonEmpty )
   }
}
