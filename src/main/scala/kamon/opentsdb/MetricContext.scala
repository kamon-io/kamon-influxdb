package kamon.opentsdb

import kamon.metric.{Entity, MetricKey}
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.InstrumentSnapshot


case class MetricContext(tick : TickMetricSnapshot, entity: Entity, metricKey: MetricKey, snapshot : InstrumentSnapshot)

