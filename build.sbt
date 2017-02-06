/* =========================================================================================
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

val kamonCore         = "io.kamon"                  %%  "kamon-core"            % "0.6.6"
val opentsdb = "net.opentsdb" % "opentsdb" % "2.3.0" excludeAll(
   ExclusionRule(organization = "ch.qos.logback"),
   ExclusionRule(organization = "com.google.gwt"),
   ExclusionRule(organization = "net.opentsdb", artifact = "opentsdb_gwt_theme"),
   ExclusionRule(organization = "org.jgrapht")
   )
val hbase = "org.hbase" % "asynchbase" % "1.7.2"
name := "kamon-opentsdb"

parallelExecution in Test in Global := false

libraryDependencies ++=
    compileScope(kamonCore, akkaDependency("slf4j").value, opentsdb, hbase) ++
    testScope(scalatest, akkaDependency("testkit").value, slf4jApi, slf4jnop,
       "org.mockito" % "mockito-all" % "1.10.19"
    )

resolvers += Resolver.bintrayRepo("kamon-io", "releases")
