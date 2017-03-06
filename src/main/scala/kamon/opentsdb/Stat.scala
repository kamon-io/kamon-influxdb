package kamon.opentsdb

import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.{Counter, Histogram, InstrumentSnapshot}

/**
  * Generates a value when given an [[InstrumentSnapshot]]
  */
sealed trait Stat {
   type T <: AnyVal
   val name : String
   def apply(ctx : MetricContext) : PartialFunction[InstrumentSnapshot, T]
}

/**
  * A stat that returns a [[Long]]
  */
trait LongStat extends Stat {
   type T = Long
}

/**
  * A stat that returns a [[Double]]
  */
trait DoubleStat extends Stat {
   type T =  Double
}

/**
  * Returns the percentile of the snapshot specified by [[name]]
  * Can only be applied to [[Histogram.Snapshot]]
  */
class PercentileStat(val name : String) extends LongStat {
   val percentile = java.lang.Long.parseLong(name)
   override def apply(ctx : MetricContext) = {
      case ss : Histogram.Snapshot => ss.percentile(percentile)
   }
}

/**
  * Returns the minimum value of the snapshot
  * Can only be applied to [[Histogram.Snapshot]]
  */
class MinStat(val name : String) extends LongStat {
   override def apply(ctx : MetricContext) = {
      case ss : Histogram.Snapshot => ss.min
   }
}

/**
  * Returns the maximum value of the snapshot
  * Can only be applied to [[Histogram.Snapshot]]
  */
class MaxStat(val name : String) extends LongStat {
   override def apply(ctx : MetricContext) = {
      case ss : Histogram.Snapshot => ss.max
   }
}

/**
  * Returns the median value of the snapshot
  * Can only be applied to [[Histogram.Snapshot]]
  */
class MedianStat(val name : String) extends LongStat {
   override def apply(ctx : MetricContext) = {
      case ss : Histogram.Snapshot => ss.percentile(50)
   }
}

/**
  * Returns the mean value of the snapshot
  * Can only be applied to [[Histogram.Snapshot]]
  */
class MeanStat(val name : String) extends LongStat {
   override def apply(ctx : MetricContext) = {
      case ss : Histogram.Snapshot if ss.numberOfMeasurements == 0 => 0L
      case ss : Histogram.Snapshot => ss.sum / ss.numberOfMeasurements
   }
}

/**
  * Returns the count of the snapshot
  * Can be applied to [[Histogram.Snapshot]] and [[Counter.Snapshot]]
  */
class CountStat(val name : String) extends LongStat {
   override def apply(ctx : MetricContext) = {
      case ss : Histogram.Snapshot => ss.numberOfMeasurements
      case ss : Counter.Snapshot => ss.count
   }
}

/**
  * Returns the count of the snapshot divided by tick interval
  * Can be applied to [[Histogram.Snapshot]] and [[Counter.Snapshot]]
  */
class RateStat(val name : String) extends DoubleStat {
   override def apply(ctx : MetricContext) = {
      case ss : Histogram.Snapshot => ss.numberOfMeasurements / divisor(ctx.tick)
      case ss : Counter.Snapshot => ss.count / divisor(ctx.tick)
   }

   def divisor(tick: TickMetricSnapshot) : Double = {
      (tick.to.millis - tick.from.millis) / 1000.0
   }

}

