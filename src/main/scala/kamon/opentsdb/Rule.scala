package kamon.opentsdb

import java.lang.management.ManagementFactory

import com.typesafe.config.Config


trait Rule {
   def apply(ctx : MetricContext, stat : Stat) : String
}

class StaticValueRule(value : String) extends Rule {
   override  def apply(ctx : MetricContext, stat : Stat) : String = value
}

class HostnameRule(config : Config) extends StaticValueRule(ManagementFactory.getRuntimeMXBean.getName.split('@')(1))

class CategoryRule(config : Config) extends Rule {
   override  def apply(ctx : MetricContext, stat : Stat) : String = ctx.entity.category
}

class EntityRule(config : Config) extends Rule {
   override  def apply(ctx : MetricContext, stat : Stat) : String = ctx.entity.name
}

class MetricRule(config : Config) extends Rule {
   override  def apply(ctx : MetricContext, stat : Stat) : String = ctx.metricKey.name
}

class StatRule(config : Config) extends Rule {
   override  def apply(ctx : MetricContext, stat : Stat) : String = stat.name
}

private[opentsdb] case class NormalizedRule(rule : Rule, normalizers : Seq[(String) => String]) extends Rule {
   override  def apply(ctx : MetricContext, stat : Stat) : String = {
      normalizers.foldLeft(rule(ctx, stat)) { (part, normalizer) => normalizer(part) }
   }
}