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

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

object FluxRefTest {
  val mappings = List(
    "develop-0.1.2" -> Some(FluxDevelopBranch(0, 1, 2)),
    "topic-0.1.2-MY-NAME" -> Some(FluxTopicBranch(0, 1, 2, "MY-NAME")),
    "release-0.1.2" -> Some(FluxReleaseTag(0, 1, 2)),
    "release-0.1.2-alpha.0" -> Some(FluxPrereleaseTag(0, 1, 2, 0)),
    // These have prerelease when they should not... or vice versa.
    "develop-0.1.2-MY-NAME" -> None,
    "topic-0.1.2" -> None,
    // These have build info, which is disallowed.
    "develop-0.1.2+001" -> None,
    "topic-0.1.2-MY-NAME+001" -> None,
    "release-0.1.2+001" -> None,
    "release-0.1.2-alpha.0+001" -> None,
    // Invalid prefix
    "prerelease-0.1.2-alpha.0" -> None,
    // Invalid semvers
    "develop-0.1.02" -> None,
    "topic-0.1.02-MY..NAME" -> None,
    "release-0.1.02" -> None,
    "release-0.1.2-alpha.00" -> None,
    // Entirely invalid
    "" -> None,
    "XXX" -> None,
  )
}

class FluxRefTest extends AnyFunSpec with Matchers {
  describe("apply") {
    FluxRefTest.mappings.foreach {
      case (s, ref) =>
        it(s"should ${if (ref.isEmpty) "not " else ""}parse '$s'") {
          FluxRef(s) shouldBe ref
        }
    }
  }
}

class FluxBranchTest extends AnyFunSpec with Matchers {
  describe("apply") {
    FluxRefTest.mappings.foreach {
      case (s, ref @ Some(_: FluxBranch)) =>
        it(s"should parse '$s'") {
          FluxBranch(s) shouldBe ref
        }
      case (s, _) =>
        it(s"should not parse '$s'") {
          FluxBranch(s) shouldBe None
        }
    }
  }
}

class FluxTagTest extends AnyFunSpec with Matchers {
  describe("apply") {
    FluxRefTest.mappings.foreach {
      case (s, ref @ Some(_: FluxTag)) =>
        it(s"should parse '$s'") {
          FluxTag(s) shouldBe ref
        }
      case (s, _) =>
        it(s"should not parse '$s'") {
          FluxTag(s) shouldBe None
        }
    }
  }

  describe("ordering") {
    it("should order by precedence") {
      val actual = List[FluxTag](
        FluxReleaseTag(0, 1, 0),
        FluxReleaseTag(1, 0, 1),
        FluxReleaseTag(2, 0, 0),
        FluxPrereleaseTag(1, 0, 1, 0),
        FluxPrereleaseTag(1, 0, 1, 1),
        FluxPrereleaseTag(2, 0, 0, 8),
      ).sorted

      val expected = List[FluxTag](
        FluxReleaseTag(0, 1, 0),
        FluxPrereleaseTag(1, 0, 1, 0),
        FluxPrereleaseTag(1, 0, 1, 1),
        FluxReleaseTag(1, 0, 1),
        FluxPrereleaseTag(2, 0, 0, 8),
        FluxReleaseTag(2, 0, 0),
      )

      actual shouldBe expected
    }
  }
}

class FluxDevelopBranchTest extends AnyFunSpec with Matchers {
  describe("apply") {
    FluxRefTest.mappings.foreach {
      case (s, ref @ Some(_: FluxDevelopBranch)) =>
        it(s"should parse '$s'") {
          FluxDevelopBranch(s) shouldBe ref
        }
      case (s, _) =>
        it(s"should not parse '$s'") {
          FluxDevelopBranch(s) shouldBe None
        }
    }
  }

  describe("unapply") {
    it(s"should match") {
      val FluxDevelopBranch(0, 1, 2) = "develop-0.1.2"
    }
    it(s"should not match") {
      a[MatchError] shouldBe thrownBy {
        val FluxDevelopBranch(_, _, _) = "xxx"
      }
    }
  }
}

class FluxTopicBranchTest extends AnyFunSpec with Matchers {
  describe("apply") {
    FluxRefTest.mappings.foreach {
      case (s, ref @ Some(_: FluxTopicBranch)) =>
        it(s"should parse '$s'") {
          FluxTopicBranch(s) shouldBe ref
        }
      case (s, _) =>
        it(s"should not parse '$s'") {
          FluxTopicBranch(s) shouldBe None
        }
    }

    it("should throw on invalid prerelease version") {
      a[IllegalArgumentException] shouldBe thrownBy(FluxTopicBranch(0, 0, 0, "///"))
    }
  }

  describe("unapply") {
    it(s"should match") {
      val FluxTopicBranch(0, 1, 2, "MY-NAME") = "topic-0.1.2-MY-NAME"
    }
    it(s"should not match") {
      a[MatchError] shouldBe thrownBy {
        val FluxTopicBranch(_, _, _, _) = "xxx"
      }
    }
  }
}

class FluxReleaseTagTest extends AnyFunSpec with Matchers {
  describe("apply") {
    FluxRefTest.mappings.foreach {
      case (s, ref @ Some(_: FluxReleaseTag)) =>
        it(s"should parse '$s'") {
          FluxReleaseTag(s) shouldBe ref
        }
      case (s, _) =>
        it(s"should not parse '$s'") {
          FluxReleaseTag(s) shouldBe None
        }
    }
  }

  describe("unapply") {
    it(s"should match") {
      val FluxReleaseTag(0, 1, 2) = "release-0.1.2"
    }
    it(s"should not match") {
      a[MatchError] shouldBe thrownBy {
        val FluxReleaseTag(_, _, _) = "xxx"
      }
    }
  }
}

class FluxPrereleaseTagTest extends AnyFunSpec with Matchers {
  describe("apply") {
    FluxRefTest.mappings.foreach {
      case (s, ref @ Some(_: FluxPrereleaseTag)) =>
        it(s"should parse '$s'") {
          FluxPrereleaseTag(s) shouldBe ref
        }
      case (s, _) =>
        it(s"should not parse '$s'") {
          FluxPrereleaseTag(s) shouldBe None
        }
    }
  }

  describe("unapply") {
    it(s"should match") {
      val FluxPrereleaseTag(0, 1, 2, 0) = "release-0.1.2-alpha.0"
    }
    it(s"should not match") {
      a[MatchError] shouldBe thrownBy {
        val FluxPrereleaseTag(_, _, _, _) = "xxx"
      }
    }
  }
}
