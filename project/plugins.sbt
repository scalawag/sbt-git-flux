// sbt-git-flow -- Copyright 2022 -- Justin Patterson
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.7")
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.4.1")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.0-M4")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.9.0")

resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("org.scalawag.sbt" %% "sbt-git-series" % "0.1.0-pre.6")
addSbtPlugin("org.scalawag.sbt" %% "sbt-build-metadata" % "0.1.0-pre.2")

addDependencyTreePlugin
