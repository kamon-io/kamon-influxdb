package kamon.opentsdb.stats

import kamon.metric.instrument.Histogram

trait Stat {
   val name : String
   def apply(snapshot : Histogram.Snapshot) : Long
}

class PercentileStat(val name : String) extends Stat {
   val percentile = java.lang.Long.parseLong(name)
   override def apply(snapshot : Histogram.Snapshot) : Long = {
     snapshot.percentile(percentile)
   }
}

class MinStat(val name : String) extends Stat {
   override def apply(snapshot : Histogram.Snapshot) : Long= {
      snapshot.min
   }
}

class MaxStat(val name : String) extends Stat {
   override def apply(snapshot : Histogram.Snapshot) : Long = {
      snapshot.max
   }
}

class MedianStat(val name : String) extends Stat {
   override def apply(snapshot : Histogram.Snapshot) : Long = {
      snapshot.percentile(50)
   }
}

class MeanStat(val name : String) extends Stat {
   override def apply(snapshot : Histogram.Snapshot) : Long = {
      if (snapshot.numberOfMeasurements == 0)
         0
      else
         snapshot.sum / snapshot.numberOfMeasurements
   }
}

