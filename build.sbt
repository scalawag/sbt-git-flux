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

val Scala212 = "2.12.21"
val Scala213 = "2.13.18"

ThisBuild / scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-language:implicitConversions")
ThisBuild / testOptions += Tests.Argument("-oDF")

ThisBuild / versionScheme := Some("early-semver")
ThisBuild / sonatypeCredentialHost := "central.sonatype.com"

val commonSettings = Seq(
  organization := "org.scalawag.sbt",
)

lazy val plugin = project
  .dependsOn(lib.jvm(Scala212))
  .settings(commonSettings)
  .settings(
    sbtPlugin := true,
    name := "sbt-git-flux",
    addSbtPlugin("com.github.sbt" % "sbt-git" % "2.1.0"),
    addSbtPlugin("ch.epfl.scala" % "sbt-version-policy" % "3.2.1"),
    addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.4"),
  )

lazy val lib =
  (projectMatrix in file("lib"))
    .settings(commonSettings)
    .settings(
      name := "sbt-git-flux-lib",
      libraryDependencies ++= Seq(
        "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2",
        "org.scalatest" %% "scalatest" % "3.2.19" % "test",
      ),
    )
    .jvmPlatform(scalaVersions = Seq(Scala212, Scala213))

lazy val `sbt-git-flux` = (project in file("."))
  .aggregate(lib.jvm(Scala212), lib.jvm(Scala213), plugin)
  .settings(
    publish / skip := true,
    dependencyDot := { null }
  )
