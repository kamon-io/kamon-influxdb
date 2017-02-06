package kamon.opentsdb.names

import java.lang.management.ManagementFactory

import com.typesafe.config.Config
import kamon.metric.{Entity, MetricKey}


trait Rule {
   def apply(entity: Entity, metricKey: MetricKey) : String
}

class StaticValueRule(value : String) extends Rule {
   override def apply(entity: Entity, metricKey: MetricKey): String = value
}

class HostnameRule(config : Config) extends StaticValueRule(ManagementFactory.getRuntimeMXBean.getName.split('@')(1))

class CategoryRule(config : Config) extends Rule {
   override def apply(entity: Entity, metricKey: MetricKey): String = entity.category
}

class EntityRule(config : Config) extends Rule {
   override def apply(entity: Entity, metricKey: MetricKey): String = entity.name
}

class MetricRule(config : Config) extends Rule {
   override def apply(entity: Entity, metricKey: MetricKey): String = metricKey.name
}

private[opentsdb] case class NormalizedRule(rule : Rule, normalizers : Seq[(String) => String]) extends Rule {
   override def apply(entity: Entity, metricKey: MetricKey): String = {
      normalizers.foldLeft(rule(entity, metricKey)) { (part, normalizer) => normalizer(part) }
   }
}