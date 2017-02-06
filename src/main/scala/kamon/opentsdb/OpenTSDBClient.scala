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
import com.typesafe.config.Config

import scala.concurrent.duration._


trait OpenTSDBClient extends Actor {
  implicit protected val as = context.system
}

class OpenTSDBDirectClient(config: Config) extends OpenTSDBClient {
  implicit protected val to = akka.util.Timeout(5 seconds)
  protected val logger = Logging(context.system, classOf[OpenTSDBDirectClient])

  protected val databaseUri = {
    val baseConfig = Map("db" -> config.getString("database"))

    val withAuth = if (config.hasPath("authentication")) {
      val authConfig = config.getConfig("authentication")

      baseConfig ++ Map(
        "u" -> authConfig.getString("user"),
        "p" -> authConfig.getString("password"))
    } else {
      baseConfig
    }

    val query = if (config.hasPath("retention-policy")) {
      withAuth ++ Map("rp" -> config.getString("retention-policy"))
    } else {
      withAuth
    }

    val queryString = query.map { case (key, value) ⇒ s"$key=$value" } match {
      case Nil ⇒ ""
      case xs  ⇒ s"?${xs.mkString("&")}"
    }

    s"${config.getString("protocol")}://${config.getString("hostname")}:${config.getString("port")}/write$queryString"
  }

  def receive = {
    case payload: String ⇒
  }
}

