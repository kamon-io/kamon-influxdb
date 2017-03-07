package kamon.opentsdb

import java.util.regex.Pattern

import akka.actor.ReflectiveDynamicAccess
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.JavaConverters._
import scala.collection.immutable
import kamon.util.ConfigTools.Syntax


/**
  * Creates [[DataPointGenerator]] based on the configuration.
  */
class DataPointGeneratorFactory(config : Config, defaultPath : String = "default") {

   val ruleLookup = {
      val dynamic = new ReflectiveDynamicAccess(getClass.getClassLoader)
      val rules = config.getConfig("rules")
      rules.firstLevelKeys.map { key =>
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


   def createDataPointGenerator(path : String): DataPointGenerator = {
      new DataPointGenerator(
         createStats(path),
         createNameGenerator(path),
         createTagGenerator(path),
         createTimestampGenerator(path))
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
      val tagConfigs = if (config.hasPath(s"$path.tags")) {
         config.getConfigList(s"$path.tags")
      } else {
         config.getConfigList(s"$defaultPath.tags")
      }
      val tagConfig = tagConfigs.asScala.headOption.getOrElse(ConfigFactory.empty())

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
      stats.asScala.toList.map { stat : String =>
         statLookup.find { case (pattern, className) =>
            pattern.matcher(stat).matches()
         } match {
            case Some((pattern, className)) => dynamic.createInstanceFor[Stat](className, immutable.Seq((classOf[String], stat))).get
            case None => throw new Exception(s"Cannot find configuration for stat '$stat'")
         }
      }
   }

   def createTimestampGenerator(path : String) = {
      val value = if (config.hasPath(s"$path.timestamp")) {
         config.getString(s"$path.timestamp")
      } else {
         config.getString(s"$defaultPath.timestamp")
      }

      value match {
         case "seconds" => SecondTimestampGenerator
         case "milliseconds" => MilliSecondTimestampGenerator
      }
   }

   implicit def configToMap(config: Config) : Map[String, String] = {
      config.root.entrySet().asScala.map { entry =>
         entry.getKey -> entry.getValue.unwrapped().toString
      }.toMap
   }
}




