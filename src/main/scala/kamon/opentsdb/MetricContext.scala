package kamon.opentsdb

import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.InstrumentSnapshot
import kamon.metric.{Entity, MetricKey}

/**
  * A utility class used to simplify sending around all the information required to generate a data point
  */
case class MetricContext(tick : TickMetricSnapshot, entity: Entity, metricKey: MetricKey, snapshot : InstrumentSnapshot)

