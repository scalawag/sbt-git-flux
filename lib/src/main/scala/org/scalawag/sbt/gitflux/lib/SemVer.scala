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

/** Represents a [[https://semver.org/ semantic version 2.0.0]].
  *
  * @param major the [[https://semver.org/#spec-item-2 major version]]
  * @param minor the [[https://semver.org/#spec-item-2 minor version]]
  * @param patch the [[https://semver.org/#spec-item-2 patch version]]
  * @param prereleaseIdentifiers the optional [[https://semver.org/#spec-item-9 pre-release identifiers]]
  * @param build the optional [[https://semver.org/#spec-item-10 build metadata]]
  */
case class SemVer(
    major: Int,
    minor: Int,
    patch: Int,
    prereleaseIdentifiers: List[SemVer.Prerelease.Identifier] = Nil,
    build: Option[String] = None
) {

  /** The prerelease version, represented as a single string. */
  val prerelease: Option[String] = {
    def eitherStr(in: Either[Int, String]): String = in.fold(_.toString, identity)
    Some(prereleaseIdentifiers.map(eitherStr).mkString(".")).filter(_.nonEmpty)
  }

  /** The string representation of this semantic version, according to
    * [[https://semver.org/#semantic-versioning-specification-semver the specification]].
    */
  override val toString: String = {
    val buildStr = build.map("+" + _).mkString
    val prereleaseStr = prerelease.map("-" + _).mkString
    s"$major.$minor.$patch$prereleaseStr$buildStr"
  }
}

object SemVer {
  import scala.util.parsing.combinator._

  object Prerelease {
    type Identifier = Either[Int, String]

    /** Parse a [[https://semver.org/#spec-item-9 pre-release version]] into its individual identifiers. */
    def parse(in: String): Parser.ParseResult[List[Identifier]] = Parser.parseAll(Parser.prerelease, in)

    /** Deconstruct a [[https://semver.org/#spec-item-9 pre-release version]] into its individual identifiers. */
    def unapply(in: String): Option[List[Identifier]] = parse(in).map(Some(_)).getOrElse(None)
  }

  object WithPrerelease {

    /** Deconstruct a semantic version into its component parts (including the pre-release identifiers). If you want
      *  the pre-release version as a simple String, you should use [[SemVer.unapply]] instead.
      */
    def unapply(in: String): Option[(Int, Int, Int, List[Prerelease.Identifier], Option[String])] =
      parse(in) match {
        case Parser.Success(s, _) => Some((s.major, s.minor, s.patch, s.prereleaseIdentifiers, s.build))
        case _                    => None
      }

  }

  object Parser extends RegexParsers {
    override def skipWhitespace: Boolean = false

    private val numeric: Parser[Int] = "0|[1-9][0-9]*".r.map(_.toInt)
    private val alphanumeric: Parser[String] = "[0-9A-Za-z-]+".r

    private val core: Parser[(Int, Int, Int)] =
      (numeric ~ '.' ~ numeric ~ '.' ~ numeric).map { case x ~ _ ~ y ~ _ ~ z => (x, y, z) }

    private val prereleaseIdentifier: Parser[Either[Int, String]] =
      numeric.map(Left(_)) | alphanumeric.map(Right(_))
    private[SemVer] val prerelease: Parser[List[Either[Int, String]]] =
      (prereleaseIdentifier ~ rep('.' ~> prereleaseIdentifier)).map { case head ~ tail => head :: tail }

    private val buildIdentifier: Parser[String] = alphanumeric

    private[SemVer] val semver: Parser[SemVer] = (core ~ ('-' ~> prerelease).? ~ ('+' ~> buildIdentifier).?).map {
      case (major, minor, patch) ~ pre ~ build => SemVer(major, minor, patch, pre.getOrElse(Nil), build)
    }
  }

  /** Parse a [[https://semver.org/ semantic version]] into its various components. */
  def parse(in: String): Parser.ParseResult[SemVer] = Parser.parseAll(Parser.semver, in)

  def parseOption(in: String): Option[SemVer] =
    parse(in) match {
      case Parser.Success(sv, _) => Some(sv)
      case _                     => None
    }

  /** Deconstruct a semantic version into its component parts. This represents the prerelease version as a string
    * so that it's easier to get the string format if that's what you need. If you need the individual identifiers,
    * you can further deconstruct it with [[Prerelease.unapply]].
    */
  def unapply(in: String): Option[(Int, Int, Int, Option[String], Option[String])] =
    parse(in) match {
      case Parser.Success(s, _) => Some((s.major, s.minor, s.patch, s.prerelease, s.build))
      case _                    => None
    }

  /** Orders semantic versions by their [[https://semver.org/#spec-item-11 precedence]]. */
  implicit val ordering: Ordering[SemVer] = {
    implicit val prereleaseOrdering: Ordering[List[Prerelease.Identifier]] = { (ll, rr) =>
      // The spec says that (as a special case, kind of) a version with _no_ prerelease identifiers sorts _higher_ than
      // one with prerelease identifiers despite the fact that normally longer ones sort higher. Handle that special
      // case here.
      (ll, rr) match {
        case (Nil, Nil) => +0
        case (Nil, _)   => +1
        case (_, Nil)   => -1
        case _          =>
          // The spec says to compare corresponding identifiers:
          //  - When they are both Strings or both Ints, compare them naturally.
          //  - When there's one of each, the Int sorts lower.
          //  - When they are different lengths, the absence of an identifier sorts lower than the presence of one.
          ll.iterator
            .map(Some(_))
            .zipAll(rr.iterator.map(Some(_)), None, None)
            .map {
              case (Some(Right(l)), Some(Right(r))) => l.compare(r)
              case (Some(Left(l)), Some(Left(r)))   => l.compare(r)
              case (Some(Left(_)), Some(Right(_)))  => -1
              case (Some(Right(_)), Some(Left(_)))  => +1
              case (None, _)                        => -1
              case (_, None)                        => +1
            }
            .find(_ != 0)
            .getOrElse(0)
      }
    }

    Ordering.by(x => (x.major, x.minor, x.patch, x.prereleaseIdentifiers, x.build))
  }
}
