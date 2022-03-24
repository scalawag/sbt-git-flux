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

package org.scalawag.sbt.gitflux.plugin

import com.typesafe.sbt.GitPlugin
import com.typesafe.sbt.SbtGit.git._
import com.typesafe.tools.mima.plugin.MimaKeys.{mimaPreviousArtifacts, mimaReportSignatureProblems}
import org.eclipse.jgit.lib.RepositoryBuilder
import org.scalawag.sbt.gitflux.lib.{FluxBranch, FluxPrereleaseTag, FluxRef, FluxReleaseTag, FluxTag, SemVer}
import sbt.Keys._
import sbt.internal.util.MessageOnlyException
import sbt.io.IO
import sbt.io.syntax._
import sbt.{AutoPlugin, Def, Keys, ThisBuild, settingKey, taskKey}
import sbt.Def._
import sbt.librarymanagement.syntax._
import sbtversionpolicy.{Compatibility, SbtVersionPolicyPlugin}
import sbtversionpolicy.SbtVersionPolicyPlugin.autoImport.versionPolicyIntention

import scala.collection.JavaConverters._
import scala.math.Ordering.Implicits._

/** This doesn't use the sbt-release plugin's snapshot dependency detection because it only detects
  *  snapshots in the top-level build. We want to fail the build even if there's a plugin at a SNAPSHOT
  *  revision.
  */
object GitFluxPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = GitPlugin && SbtVersionPolicyPlugin

  final case class Compat(compatibility: Compatibility, version: SemVer)

  val gitFluxRef = settingKey[FluxRef]("the git ref chosen to derive the version")

  val compatibilityInfo =
    settingKey[Option[Compat]](
      "single parameter to capture all the versioncheck/mima parameters inferred from the version"
    )

  val gitFluxPriorReleaseVersion =
    settingKey[Option[SemVer]]("the most advanced, earlier release (derived from git release tags))")

  object autoImport {
    val gitFluxVersionFile = taskKey[File]("the location of the generated version written by gitFluxWriteVersion")
    val gitFluxWriteVersion = taskKey[File]("write the (derived) version to the filesystem at gitFluxVersionFile")
    val gitFluxArtifactSince = settingKey[Option[String]]("the earliest version in which this project appeared")
    val gitFluxLegacyTagMapper = settingKey[PartialFunction[String, FluxReleaseTag]](
      "allows addition tags (pre git-flux) to be mapped to SemVers for the purpose of finding old releases"
    )
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] =
    Seq[Def.Setting[_]](
      //----------------------------------------------------------------------------------------------------------------
      // Use the jgit instead of relying on the version of git that's in the path.
      GitPlugin.autoImport.useJGit,
      //----------------------------------------------------------------------------------------------------------------
      // Generate the version from the git metadata
      ThisBuild / gitFluxRef := {
        val currentTags = gitCurrentTags.value
        val currentBranch = gitCurrentBranch.value

        // We are only concerned with branches that match our develop RE and tags that match our release RE.

        val branches = Option(currentBranch)
          .map(s => s -> FluxRef(s))
          .collect {
            case (s, Some(b: FluxBranch)) =>
              Some(b) -> s"  $s (eligible)"
            case (s, _) =>
              None -> s"  $s (ineligible, not in 'develop-X.Y.Z' or 'topic-X.Y.Z-name' format)"
          }
          .toList

        val tags = currentTags
          .map(s => s -> FluxRef(s))
          .collect {
            case (s, Some(tag: FluxTag)) =>
              Some(tag) -> s"  $s -> ${tag.version} (eligible)"
            case (s, _) =>
              None -> s"  $s (ineligible, not in 'release-X.Y.Z' or 'release-X.Y.Z-alpha.N' format)"
          }
          .toList
          .sorted

        val eligibleRefs = (branches.flatMap(_._1) ::: tags.flatMap(_._1)).distinct

        // Make sure this all gets logged in a single block (without other logs interspersed)
        val debugMessage = Iterable(
          Iterable(s"current branch:"),
          branches.map(_._2),
          Iterable(s"current tags:"),
          tags.map(_._2),
          Iterable(s"eligible versions (in order of preference):"),
          eligibleRefs.map(v => s"  $v")
        ).flatten.mkString("GitFluxPlugin:\n  ", "\n  ", "")

        val derived = eligibleRefs.headOption.getOrElse {
          val msg = s"""
            |Unable to determine a version from the git metadata. One of the following must be true:
            | - the current branch follows either the format 'develop-X.Y.Z' or 'topic-X.Y.Z-name'
            | - there is a tag at HEAD that follows either the format 'release-X.Y.Z' or 'release-X.Y.Z-alpha.N'
          """.stripMargin
          throw new MessageOnlyException(msg)
        }

        Keys.sLog.value.debug(debugMessage)

        derived
      },
//      ThisBuild / gitFluxVersion := (ThisBuild / gitFluxRef).value.version,
      ThisBuild / version := {
        val ref = (ThisBuild / gitFluxRef).value
        val ver = ref match {
          case b: FluxBranch => s"${b.version}-SNAPSHOT"
          case t: FluxTag    => s"${t.version}"
        }
        Keys.sLog.value.info(s"GitFluxPlugin: derived version $ver based on git ref ${ref.refName}")
        ver
      },
      // By default, there's no legacy tag mapping. The only tags considered are those that match git-flux's patterns.
      ThisBuild / gitFluxLegacyTagMapper := { case _ if false => ??? },
      // If we don't define isSnapshot on ThisBuild, it apparently thinks everything is _not_ a snapshot.
      ThisBuild / isSnapshot := (ThisBuild / version).value.endsWith("-SNAPSHOT"),
      ThisBuild / gitFluxVersionFile := {
        (ThisBuild / baseDirectory).value / "target" / "VERSION"
      },
      ThisBuild / gitFluxWriteVersion := {
        val out = (ThisBuild / gitFluxVersionFile).value

        IO.write(out, version.value)
        Keys.sLog.value.info(s"GitFluxPlugin: Wrote derived version to $out")
        out
      },
      // By default, all artifacts have always existed, so always perform the mima check against the calculated version.
      gitFluxArtifactSince := None,
      // Once for the entire build, we need to look through the tags in the repo that match our pattern to see what
      // prior release exists for our current version.
      ThisBuild / gitFluxPriorReleaseVersion := {
        val repo = new RepositoryBuilder().findGitDir(baseDirectory.value).build()
        val git = org.eclipse.jgit.api.Git.wrap(repo)
        try {
          val releaseMapper = (ThisBuild / gitFluxLegacyTagMapper).value

          def refToTag(ref: String): Option[FluxTag] = FluxTag(ref).orElse(releaseMapper.lift(ref))

          val releaseTags: List[(Option[FluxTag], String)] =
            git.tagList().call().asScala.toList.map(_.getName.stripPrefix("refs/tags/")).map { ref =>
              refToTag(ref)
                .map {
                  case t: FluxReleaseTag =>
                    Some(t) -> s"$ref -> ${t.version}"
                  case t: FluxPrereleaseTag =>
                    None -> s"$ref (ignoring prerelease tag)"
                }
                .getOrElse {
                  None -> s"$ref (ignoring tag that doesn't begin with 'release-')"
                }
            }

          val allReleases = releaseTags.flatMap(_._1).sorted.distinct

          val targetReleaseVersion = gitFluxRef.value.targetReleaseVersion

          val prior = allReleases.takeWhile(_.version <= targetReleaseVersion).lastOption.map(_.version)

          val debugMessage =
            releaseTags.map(_._2).mkString("GitFluxPlugin: looking for prior release tags in git:\n  ", "\n  ", "")

          val log = Keys.sLog.value
          log.debug(debugMessage)
          log.info(
            prior.map { p =>
              s"GitFluxPlugin: chose prior release $p"
            } getOrElse {
              s"GitFluxPlugin: no prior release for this target version"
            }
          )

          prior
        } finally {
          git.close()
          repo.close()
        }
      },
      // Run once for each project. Based on the target release version, decide what level of backward compatibility
      // to check for and against what release. Check out the match statement below for the rules.
      compatibilityInfo := {
        val log = sLog.value

        val header = s"GitFluxPlugin: ${name.value} for Scala ${scalaVersion.value} at version ${version.value}"

        val thisVersion = (ThisBuild / gitFluxRef).value.version
        val priorVersion = (ThisBuild / gitFluxPriorReleaseVersion).value
        val sinceVersion = gitFluxArtifactSince.value.flatMap { s =>
          SemVer.parseOption(s) match {
            case Some(ver) =>
              Some(ver)
            case None =>
              log.error(s"Ignoring mimaArtifactSince value '$s' because it is not a core SemVer (x.y.z)")
              None
          }
        }

        val noCompatMessage = "No compatibility check will be performed."

        def handlePrior(
            firstVersion: SemVer,
            intro: String,
            seriesType: String,
            compatibility: Compatibility
        ): (Option[Compat], Iterable[String]) =
          priorVersion match {
            case None =>
              None -> Iterable(intro, "No prior release exists.", noCompatMessage)
            case Some(prior) if prior < firstVersion =>
              None -> Iterable(intro, s"Prior version $prior is not part of this $seriesType series.", noCompatMessage)
            case Some(prior) if sinceVersion.exists(_ > prior) =>
              None -> Iterable(
                intro,
                s"Prior version $prior is before the introduction of this artifact (${sinceVersion.get}) according to mimaArtifactSince.",
                "No compatibility check will be performed."
              )
            case Some(prior) =>
              val compat = Compat(compatibility, prior)
              Some(compat) -> Iterable(
                intro,
                s"The prior version appears to be $prior.",
                sinceVersion
                  .map(s => s"This artifact has been present since $s.")
                  .getOrElse("This artifact has always been present."),
                s"${compat.compatibility} will be checked against version ${compat.version}"
              )
          }

        val (compat, debugLines): (Option[Compat], Iterable[String]) =
          thisVersion match {
            case SemVer(0, y, 0, _, None) =>
              None -> Iterable(
                s"This is the first release in early minor series 0.$y.x, so no compatibility is guaranteed."
              )

            case SemVer(x, 0, 0, _, None) =>
              None -> Iterable(s"This is the first release in major series $x.y.z, so no compatibility is guaranteed.")

            case SemVer(x, y, 0, _, None) =>
              handlePrior(
                firstVersion = SemVer(x, y - 1, 0),
                intro =
                  s"This is the first release in minor series $x.$y.x, so binary compatibility is required with the prior release in its major series.",
                seriesType = "major",
                Compatibility.BinaryCompatible
              )

            case SemVer(x, y, _, _, None) =>
              handlePrior(
                firstVersion = SemVer(x, y, 0),
                intro =
                  s"This is a patch release in minor series $x.$y.x, so source compatibility is required with the prior release in its minor series",
                seriesType = "minor",
                Compatibility.BinaryAndSourceCompatible
              )

            case v =>
              None -> Iterable(s"Version $v is not a SemVer (x.y.z).", noCompatMessage)
          }

        val infoMessage = compat
          .map { c =>
            s"$header must be ${c.compatibility} with ${c.version}"
          }
          .getOrElse {
            s"$header has no compatibility guarantees"
          }

        val debugMessage: String = debugLines.mkString(s"$infoMessage:\n  ", "\n  ", "")
        log.debug(debugMessage)
        log.info(infoMessage)

        compat
      },
      // These are set for each project (by copying the global values above into the sbt-version-policy keys)
      mimaReportSignatureProblems := true,
      versionPolicyIntention := compatibilityInfo.value.map(_.compatibility).getOrElse(Compatibility.None),
      mimaPreviousArtifacts :=
        compatibilityInfo.value.map(_.version.toString).map(organization.value %% moduleName.value % _).toSet,
    )
}
