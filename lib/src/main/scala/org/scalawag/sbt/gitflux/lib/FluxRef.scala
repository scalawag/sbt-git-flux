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

package org.scalawag.sbt.gitflux.lib

sealed trait FluxRef {
  val major: Int
  val minor: Int
  val patch: Int

  protected val refPrefix: String
  val version: SemVer
  val targetReleaseVersion: SemVer
  lazy val refName: String = s"$refPrefix$version"
}

object FluxRef {
  def apply(in: String): Option[FluxRef] = Iterable(FluxTag(in), FluxBranch(in)).flatten.headOption
}

sealed trait FluxBranch extends FluxRef

object FluxBranch {
  def apply(in: String): Option[FluxBranch] = Iterable(FluxDevelopBranch(in), FluxTopicBranch(in)).flatten.headOption
}

case class FluxTopicBranch(major: Int, minor: Int, patch: Int, topic: String) extends FluxBranch {
  private val prerelease = SemVer.Prerelease.parse(topic).getOrElse {
    throw new IllegalArgumentException(
      s"topic '$topic' is not a valid semantic version pre-release (https://semver.org/#spec-item-9)"
    )
  }
  override val refPrefix: String = FluxTopicBranch.PREFIX
  override val version: SemVer = SemVer(major, minor, patch, prerelease)
  override val targetReleaseVersion: SemVer = version.copy(prereleaseIdentifiers = Nil)
}

object FluxTopicBranch {
  protected val PREFIX: String = "topic-"

  def apply(in: String): Option[FluxTopicBranch] =
    if (!in.startsWith(PREFIX)) None
    else
      in.stripPrefix(PREFIX) match {
        case SemVer(x, y, z, Some(pre @ SemVer.Prerelease(_ :: _)), None) => Some(FluxTopicBranch(x, y, z, pre))
        case _                                                            => None
      }

  def unapply(in: String): Option[(Int, Int, Int, String)] = apply(in).map(b => (b.major, b.minor, b.patch, b.topic))
}

case class FluxDevelopBranch(major: Int, minor: Int, patch: Int) extends FluxBranch {
  override val refPrefix: String = FluxDevelopBranch.PREFIX
  override val version: SemVer = SemVer(major, minor, patch)
  override val targetReleaseVersion: SemVer = version
}

object FluxDevelopBranch {
  protected val PREFIX: String = "develop-"

  def apply(in: String): Option[FluxDevelopBranch] =
    if (!in.startsWith(PREFIX)) None
    else
      in.stripPrefix(PREFIX) match {
        case SemVer(x, y, z, None, None) => Some(FluxDevelopBranch(x, y, z))
        case _                           => None
      }

  def unapply(in: String): Option[(Int, Int, Int)] = apply(in).map(b => (b.major, b.minor, b.patch))
}

sealed trait FluxTag extends FluxRef

object FluxTag {
  def apply(in: String): Option[FluxTag] = Iterable(FluxReleaseTag(in), FluxPrereleaseTag(in)).flatten.headOption

  implicit val ordering: Ordering[FluxTag] = Ordering.by(_.version)
}

case class FluxReleaseTag(major: Int, minor: Int, patch: Int) extends FluxTag {
  override val refPrefix: String = "release"
  override val version: SemVer = SemVer(major, minor, patch)
  override val targetReleaseVersion: SemVer = version
}

object FluxReleaseTag {
  protected val PREFIX: String = "release-"

  def apply(in: String): Option[FluxReleaseTag] =
    if (!in.startsWith(PREFIX)) None
    else
      in.stripPrefix(PREFIX) match {
        case SemVer(x, y, z, None, None) => Some(FluxReleaseTag(x, y, z))
        case _                           => None
      }

  def unapply(in: String): Option[(Int, Int, Int)] =
    apply(in).map(t => (t.major, t.minor, t.patch))
}

case class FluxPrereleaseTag(major: Int, minor: Int, patch: Int, alpha: Int) extends FluxTag {
  override val refPrefix: String = "release"
  override val version: SemVer = SemVer(major, minor, patch, List(Right("alpha"), Left(alpha)))
  override val targetReleaseVersion: SemVer = version.copy(prereleaseIdentifiers = Nil)
}

object FluxPrereleaseTag {
  protected val PREFIX: String = "release-"

  def apply(in: String): Option[FluxPrereleaseTag] =
    if (!in.startsWith(PREFIX)) None
    else
      in.stripPrefix(PREFIX) match {
        case SemVer(x, y, z, Some(SemVer.Prerelease(List(Right("alpha"), Left(n)))), None) =>
          Some(FluxPrereleaseTag(x, y, z, n))
        case _ => None
      }

  def unapply(in: String): Option[(Int, Int, Int, Int)] =
    apply(in) map (t => (t.major, t.minor, t.patch, t.alpha))
}
