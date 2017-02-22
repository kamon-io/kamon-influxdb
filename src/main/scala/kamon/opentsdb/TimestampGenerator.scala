package kamon.opentsdb

trait TimestampGenerator {
   def apply(ctx: MetricContext, stat: Stat): Long
}

object SecondTimestampGenerator extends TimestampGenerator {
   override def apply(ctx: MetricContext, stat: Stat): Long = {
      ctx.tick.to.toTimestamp.seconds
   }
}

object MilliSecondTimestampGenerator extends TimestampGenerator {
   override def apply(ctx: MetricContext, stat: Stat): Long = {
      ctx.tick.to.millis
   }
}
