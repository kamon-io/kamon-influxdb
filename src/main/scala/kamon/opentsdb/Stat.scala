package kamon.opentsdb

import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.{Counter, Histogram, InstrumentSnapshot}

sealed trait Stat {
   type T <: AnyVal
   val name : String
   def apply(ctx : MetricContext) : PartialFunction[InstrumentSnapshot, T]
}

trait LongStat extends Stat {
   type T = Long
}
trait DoubleStat extends Stat {
   type T =  Double
}

class PercentileStat(val name : String) extends LongStat {
   val percentile = java.lang.Long.parseLong(name)
   override def apply(ctx : MetricContext) = {
      case ss : Histogram.Snapshot => ss.percentile(percentile)
   }
}

class MinStat(val name : String) extends LongStat {
   override def apply(ctx : MetricContext) = {
      case ss : Histogram.Snapshot => ss.min
   }
}

class MaxStat(val name : String) extends LongStat {
   override def apply(ctx : MetricContext) = {
      case ss : Histogram.Snapshot => ss.max
   }
}

class MedianStat(val name : String) extends LongStat {
   override def apply(ctx : MetricContext) = {
      case ss : Histogram.Snapshot => ss.percentile(50)
   }
}

class MeanStat(val name : String) extends LongStat {
   override def apply(ctx : MetricContext) = {
      case ss : Histogram.Snapshot if ss.numberOfMeasurements == 0 => 0L
      case ss : Histogram.Snapshot => ss.sum / ss.numberOfMeasurements
   }
}

class CountStat(val name : String) extends LongStat {
   override def apply(ctx : MetricContext) = {
      case ss : Histogram.Snapshot => ss.numberOfMeasurements
      case ss : Counter.Snapshot => ss.count
   }
}

class RateStat(val name : String) extends DoubleStat {
   override def apply(ctx : MetricContext) = {
      case ss : Histogram.Snapshot => ss.numberOfMeasurements / divisor(ctx.tick)
      case ss : Counter.Snapshot => ss.count / divisor(ctx.tick)
   }

   def divisor(tick: TickMetricSnapshot) : Double = {
      (tick.to.millis - tick.from.millis) / 1000.0
   }

}

