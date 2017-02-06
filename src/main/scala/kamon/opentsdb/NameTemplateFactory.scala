package kamon.opentsdb

import java.util.regex.Pattern

import akka.actor.ReflectiveDynamicAccess
import com.typesafe.config.Config
import kamon.metric.instrument.{Counter, Histogram, InstrumentSnapshot}
import kamon.metric.{Entity, MetricKey}
import kamon.opentsdb.names.{NormalizedRule, Rule, StaticValueRule}
import kamon.opentsdb.stats.Stat

import scala.collection.JavaConverters._
import scala.collection.immutable

class NameTemplateFactory(config : Config, defaultPath : String = "default.metric") {

   val ruleLookup = {
      val dynamic = new ReflectiveDynamicAccess(getClass.getClassLoader)
      val rules = config.getConfig("rules")
      rules.root().keySet().asScala.map { key =>
         val generator = if (rules.hasPath(s"$key.value")) {
            new StaticValueRule(rules.getString(s"$key.value"))
         } else {
            dynamic.createInstanceFor[Rule](rules.getString(s"$key.generator"), immutable.Seq((classOf[Config], config))).get
         }
         key -> generator
      }.toMap
   }

   val statLookup : List[(Pattern, String)] = {
      val rules = config.getConfig("stats")
      rules.root().entrySet().asScala.toList.map { entry =>
         Pattern.compile(entry.getKey) -> entry.getValue.unwrapped().toString
      }
   }

   val nameSeparator = config.getString("name.separator")
   val nameNormalizers = config.getConfig("name.normalizers").map { case (key, value) =>
      (s : String) => s.replaceAll(key, value)
   }.toSeq

   val tagNormalizers = config.getConfig("tag.normalizers").map { case (key, value) =>
      (s : String) => s.replaceAll(key, value)
   }.toSeq


   def createCounterGenerator() = {
      val nameGenerator = createNameGenerator("metric.counter")
      val tagGenerator = createTagGenerator("metric.counter")

      new CounterDataPointGenerator(nameGenerator, tagGenerator)
   }

   def createHistogramGenerator(path : String): HistogramDataPointGenerator = {
      val nameGenerator = createNameGenerator(path)
      val tagGenerators = createTagGenerator(path)
      val stats = createStats(path)

      new HistogramDataPointGenerator(true, "stat", stats, nameGenerator, tagGenerators)
   }

   def createNameGenerator(path : String) : MetricNameGenerator = {
      val list = if (config.hasPath(s"$path.name")) {
         config.getStringList(s"$path.name")
      } else {
         config.getStringList(s"$defaultPath.name")
      }

      val rules = list.asScala.toList.map(ruleLookup).map { NormalizedRule(_, nameNormalizers) }

      new MetricNameGenerator(rules, nameSeparator)
   }

   def createTagGenerator(path : String) : MetricTagGenerator = {
      val tagConfig = if (config.hasPath(s"$path.tags")) {
         config.getConfig(s"$path.tags")
      } else {
         config.getConfig(s"$defaultPath.tags")
      }

      val generators = configToMap(tagConfig).collect {
         case (key, value) => key -> NormalizedRule(ruleLookup(value), tagNormalizers)
      }

      new MetricTagGenerator(generators)
   }

   def createStats(path : String) : Seq[Stat] = {
      val stats = if (config.hasPath(s"$path.tags")) {
         config.getStringList(s"$path.stats")
      } else {
         config.getStringList(s"$defaultPath.stats")
      }

      val dynamic = new ReflectiveDynamicAccess(getClass.getClassLoader)
      stats.asScala.toList.flatMap { stat : String =>
         statLookup.find { case (pattern, className) =>
            pattern.matcher(stat).matches()
         }.map { _._2 }.map { className =>
            dynamic.createInstanceFor[Stat](className, immutable.Seq((classOf[String], stat))).get
         }
      }
   }

   implicit def configToMap(config: Config) : Map[String, String] = {
      config.root.entrySet().asScala.map { entry =>
         entry.getKey -> entry.getValue.unwrapped().toString
      }.toMap
   }
}




