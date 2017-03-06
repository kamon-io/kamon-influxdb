package kamon.opentsdb

/**
  * Generates the timestamp of a [[DataPoint]]
  */
trait TimestampGenerator {
   def apply(ctx: MetricContext, stat: Stat): Long
}

/**
  * Provides a timestamp at the end of the tick to the precision of the nearest second
  */
object SecondTimestampGenerator extends TimestampGenerator {
   override def apply(ctx: MetricContext, stat: Stat): Long = {
      ctx.tick.to.toTimestamp.seconds
   }
}

/**
  * Provides a timestamp at the end of the tick to the precision of the nearest millisecond
  */
object MilliSecondTimestampGenerator extends TimestampGenerator {
   override def apply(ctx: MetricContext, stat: Stat): Long = {
      ctx.tick.to.millis
   }
}
