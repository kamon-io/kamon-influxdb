package kamon.opentsdb

/**
  * Generates the name of the metric in OpenTSDB
  */
class MetricNameGenerator(nameGenerators : Seq[NormalizedRule], separator: String) {
   def apply(ctx : MetricContext, stat : Stat) : String = {
      nameGenerators.map(_.apply(ctx, stat)).filter { _.nonEmpty }.mkString(separator)
   }
}
