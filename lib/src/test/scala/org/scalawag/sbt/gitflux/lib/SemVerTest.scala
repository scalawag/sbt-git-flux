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

import org.scalatest.Inside.inside
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import SemVer.Parser.NoSuccess
import org.scalawag.sbt.gitflux.lib.SemVer.Prerelease

class SemVerTest extends AnyFunSpec with Matchers {

  describe("parsing") {
    val validSemVers = List(
      "1.2.3--.-.-.-.-+------",
      "1.2.3------+------",
      "1.2.3",
      "1.2.3-JIRA-182",
      "1.2.3-jp.JIRA-182",
      "11.22.13",
      "11.22.13-alpha.1",
      "11.22.13-alpha.11",
      "11.22.13-alpha.1.1",
      "11.22.13-alpha.0",
      "11.22.13-alpha.8",
      "11.22.13-jp.JIRA-182",
      "11.22.13-jp.JIRA-182+20220222",
      "11.22.13-jp.JIRA-182+2022-0222",
    )

    val invalidSemVers = List(
      "1.2" -> 3, // No patch version.
      "1.2." -> 4, // Ditto, but more egregious.
      "11.22.13-alpha.01" -> 16, // No leading zeroes.
      "11.22.13-jp/JIRA-182+20220222" -> 11, // No slashes in prerelease.
      "11.22.13-jp.JIRA-182+2022.0222" -> 25, // No dots in build metadata.
      " 1.2.3" -> 0, // Make sure whitespace is significant.
    )

    validSemVers.foreach { sv =>
      it(s"should parse '$sv'") {
        SemVer.parse(sv).getOrElse(fail("semver should have parsed successfully")).toString shouldBe sv
      }
    }

    invalidSemVers.foreach {
      case (sv, offset) =>
        it(s"should not parse '$sv'") {
          inside(SemVer.parse(sv)) {
            case f: NoSuccess if f.next.offset == offset => succeed
            case f: NoSuccess                            => f.next.offset shouldBe offset
            case x                                       => fail(s"semver should have failed at offset $offset - $x")
          }
        }
    }
  }

  describe("ordering by precedence") {
    import SemVer.ordering

    it("major version difference") {
      SemVer(0, 1, 1) shouldBe <(SemVer(1, 1, 1))
    }

    it("minor version difference") {
      SemVer(0, 0, 1) shouldBe <(SemVer(0, 1, 1))
    }

    it("patch version difference") {
      SemVer(0, 0, 0) shouldBe <(SemVer(0, 0, 1))
    }

    it("prerelease numbers") {
      SemVer(1, 0, 0, List(Left(0))) shouldBe <(SemVer(1, 0, 0, List(Left(1))))
    }

    it("prerelease strings") {
      SemVer(1, 0, 0, List(Right("a"))) shouldBe <(SemVer(1, 0, 0, List(Right("b"))))
    }

    it("prerelease number v. string") {
      SemVer(1, 0, 0, List(Left(1))) shouldBe <(SemVer(1, 0, 0, List(Right("a"))))
    }

    it("prerelease identifier by identifier") {
      SemVer(1, 0, 0, List(Left(0), Left(0), Left(0))) shouldBe <(SemVer(1, 0, 0, List(Left(0), Left(0), Left(1))))
    }

    it("prerelease fewer v. more identifiers") {
      SemVer(1, 0, 0, List(Left(0), Left(0))) shouldBe <(SemVer(1, 0, 0, List(Left(0), Left(0), Left(0))))
    }

    it("prerelease v. no prerelease (less than)") {
      SemVer(1, 0, 0, List(Left(0))) shouldBe <(SemVer(1, 0, 0))
    }

    it("prerelease v. no prerelease (greater than)") {
      SemVer(1, 0, 0) shouldBe >(SemVer(1, 0, 0, List(Left(0))))
    }

    it("build metadata (with prerelease)") {
      SemVer(1, 0, 0, List(Left(0)), Some("ABC")) shouldBe <(SemVer(1, 0, 0, List(Left(0)), Some("BCD")))
    }

    it("build metadata (without prerelease)") {
      SemVer(1, 0, 0, Nil, Some("ABC")) shouldBe <(SemVer(1, 0, 0, Nil, Some("BCD")))
    }
  }

  describe("unapply") {

    describe("SemVer") {

      it("should match") {
        "1.2.3-alpha.0+00001" match {
          case SemVer(1, 2, 3, Some("alpha.0"), Some("00001")) => succeed
          case _                                               => fail("pattern should have matched")
        }
      }

      it("should match with empty pre-release") {
        "1.2.3+00001" match {
          case SemVer(1, 2, 3, None, Some("00001")) => succeed
          case _                                    => fail("pattern should have matched")
        }
      }

      it("should not match when the version is invalid") {
        "00.00.00" match {
          case SemVer(_, _, _, _, _) => fail("should have failed to match")
          case _                     => succeed
        }
      }

      it("should not match when the prerelease is invalid") {
        "1.2.3-/+00001" match {
          case SemVer(_, _, _, _, _) => fail("should have failed to match")
          case _                     => succeed
        }
      }
    }

    describe("SemVer.WithPrerelease") {
      it("should match") {
        "1.2.3-alpha.0+00001" match {
          case SemVer.WithPrerelease(1, 2, 3, List(Right("alpha"), Left(0)), Some("00001")) => succeed
          case _                                                                            => fail("pattern should have matched")
        }
      }

      it("should match with empty pre-release") {
        "1.2.3+00001" match {
          case SemVer.WithPrerelease(1, 2, 3, Nil, Some("00001")) => succeed
          case _                                                  => fail("pattern should have matched")
        }
      }

      it("should not match when the version is invalid") {
        "X" match {
          case SemVer.WithPrerelease(_, _, _, _, _) => fail("should have failed to match")
          case _                                    => succeed
        }
      }

      it("should not match when the prerelease is invalid") {
        "1.2.3-/+00001" match {
          case SemVer.WithPrerelease(_, _, _, _, _) => fail("should have failed to match")
          case _                                    => succeed
        }
      }
    }

    describe("SemVer.Prerelease") {
      it("should match") {
        "alpha.0" match {
          case Prerelease(List(Right("alpha"), Left(0))) => succeed
          case _                                         => fail("pattern should have matched")
        }
      }

      it("should not match with bad prerelease version") {
        "alpha..0" match {
          case Prerelease(_) => fail("pattern should not have matched")
          case _             => succeed
        }
      }

    }
  }
}
