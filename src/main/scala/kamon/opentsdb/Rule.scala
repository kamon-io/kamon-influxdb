package kamon.opentsdb

import java.lang.management.ManagementFactory

import com.typesafe.config.Config

/**
  * Generate parts of metric names and tag values.
  */
trait Rule {
   def apply(ctx : MetricContext, stat : Stat) : String
}

// Always returns a static value
class StaticValueRule(value : String) extends Rule {
   override  def apply(ctx : MetricContext, stat : Stat) : String = value
}

// Always returns the name of the current host
class HostnameRule(config : Config) extends
   StaticValueRule(ManagementFactory.getRuntimeMXBean.getName.split('@')(1))

/**
  * Returns [[kamon.metric.Entity.category]]
  */
class CategoryRule(config : Config) extends Rule {
   override  def apply(ctx : MetricContext, stat : Stat) : String = ctx.entity.category
}

/**
  * Returns [[kamon.metric.Entity.name]]
  */
class EntityRule(config : Config) extends Rule {
   override  def apply(ctx : MetricContext, stat : Stat) : String = ctx.entity.name
}

/**
  * Returns [[kamon.metric.MetricKey.name]]
  */
class MetricRule(config : Config) extends Rule {
   override  def apply(ctx : MetricContext, stat : Stat) : String = ctx.metricKey.name
}

/**
  * Returns [[Stat.name]]
  */
class StatRule(config : Config) extends Rule {
   override def apply(ctx : MetricContext, stat : Stat) : String = stat.name
}

/**
  * Normalizes the result of another rule
  */
private[opentsdb] case class NormalizedRule(rule : Rule, normalizers : Seq[(String) => String]) extends Rule {
   override  def apply(ctx : MetricContext, stat : Stat) : String = {
      normalizers.foldLeft(rule(ctx, stat)) { (part, normalizer) => normalizer(part) }
   }
}