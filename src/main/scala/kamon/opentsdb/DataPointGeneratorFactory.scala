package kamon.opentsdb

import java.util.regex.Pattern

import akka.actor.ReflectiveDynamicAccess
import com.typesafe.config.{Config, ConfigFactory}
import kamon.util.ConfigTools.Syntax

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.util.Try


/**
  * Creates [[DataPointGenerator]] based on the configuration.
  */
class DataPointGeneratorFactory(config : Config, defaultPath : String = "default") {

   val defaults = config.getConfig(defaultPath)

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


   def createCategoryGenerator(category : String) : DataPointGenerator = {
      val categoryConfig = Try(config.getConfig(s"""category."$category"""")).getOrElse(ConfigFactory.empty())
      createDataPointGenerator(categoryConfig.withFallback(defaults))
   }

   def createMetricGenerator(metricName : String, category : String) : DataPointGenerator = {
      val metricConfig = config.getConfig(s"""metrics."$metricName"""")
      val categoryConfig = Try(config.getConfig(s"""category."$category"""")).getOrElse(ConfigFactory.empty())

      createDataPointGenerator(metricConfig.withFallback(categoryConfig).withFallback(defaults))
   }


   def createDataPointGenerator(generatorConfig : Config): DataPointGenerator = {
      var generator : DataPointGenerator = new DataPointGeneratorImpl(
         createStats(generatorConfig),
         createNameGenerator(generatorConfig),
         createTagGenerator(generatorConfig),
         createTimestampGenerator(generatorConfig))

      if (generatorConfig.getBoolean("filterIdle")) {
         generator = new NonIdleDecorator(generator)
      }
      generator
   }

   def createNameGenerator(generatorConfig : Config) : MetricNameGenerator = {
      val list = generatorConfig.getStringList(s"name")

      val rules = list.asScala.toList.map(ruleLookup).map { NormalizedRule(_, nameNormalizers) }

      new MetricNameGenerator(rules, nameSeparator)
   }

   def createTagGenerator(generatorConfig : Config) : MetricTagGenerator = {
      val list = generatorConfig.getStringList(s"tags")

      val generators = list.asScala.toList.map { name =>
         name -> NormalizedRule(ruleLookup(name), tagNormalizers)
      }.toMap

      new MetricTagGenerator(generators)
   }

   def createStats(generatorConfig : Config) : Seq[Stat] = {
      val stats = generatorConfig.getStringList("stats")

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

   def createTimestampGenerator(generatorConfig : Config) = {
      generatorConfig.getString("timestamp")  match {
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




