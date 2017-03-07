/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.opentsdb

import akka.actor._
import akka.event.Logging
import kamon.Kamon
import kamon.util.ConfigTools.Syntax

import scala.collection.JavaConverters._

/**
  * OpenTSDB Extension
  */
object OpenTSDB extends ExtensionId[OpenTSDBExtension] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): OpenTSDBExtension = new OpenTSDBExtension(system)
  override def lookup(): ExtensionId[_ <: Extension] = OpenTSDB
}

class OpenTSDBExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  implicit val actorSystem = system

  Logging(system, classOf[OpenTSDBExtension]).info("Starting the Kamon(OpenTSDB) extension")

  private val openTSDBConfig = system.settings.config.getConfig("kamon.opentsdb")
  val sender = new DirectDataPointSender(openTSDBConfig.getString("direct.quorum"))
  protected val metricsListener = system.actorOf(DataPointGeneratingActor.props(openTSDBConfig, sender), "opentsdb-metrics-generator")

  protected val subscriptions = openTSDBConfig.getConfig("subscriptions")

  subscriptions.firstLevelKeys.foreach { subscriptionCategory ⇒
    subscriptions.getStringList(subscriptionCategory).asScala.foreach { pattern ⇒
      Kamon.metrics.subscribe(subscriptionCategory, pattern, metricsListener, permanently = true)
    }
  }

  actorSystem.registerOnTermination {
    Logging(actorSystem, classOf[OpenTSDBExtension]).info("Shutting down TSDB sender")
    sender.shutdown()
  }
}
