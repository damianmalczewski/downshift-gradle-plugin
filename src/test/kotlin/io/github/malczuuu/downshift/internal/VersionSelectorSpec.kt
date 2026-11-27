/*
 * Copyright 2026-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.malczuuu.downshift.internal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.api.InvalidUserDataException

class VersionSelectorSpec :
    FunSpec(
        {
          test("parses latest patch of a line") {
            val version = VersionSelector.parse("4.0.+")

            version shouldBe VersionSelector("4.0.+", VersionLine(4, 0), dynamic = true)
            version.taskNameSegment shouldBe "4_0"
          }

          test("parses exact version") {
            val version = VersionSelector.parse(" 4.0.8 ")

            version shouldBe VersionSelector("4.0.8", VersionLine(4, 0), dynamic = false)
            version.taskNameSegment shouldBe "4_0_8"
          }

          test("parses exact version with more segments and qualifiers") {
            VersionSelector.parse("2.18.4.1").taskNameSegment shouldBe "2_18_4_1"
            VersionSelector.parse("4.2.0-RC1").taskNameSegment shouldBe "4_2_0_RC1"
            VersionSelector.parse("5.3.39.RELEASE").taskNameSegment shouldBe "5_3_39_RELEASE"
            VersionSelector.parse("4.2.0-RC1").line shouldBe VersionLine(4, 2)
          }

          listOf("4", "4.0", "4.+", "4.0.x", "[4.0,4.1)", "latest.release", "+", "", "4.0.+.1", "4.0.1-").forEach {
              value ->
            test("rejects version '$value' neither latest patch nor exact") {
              shouldThrow<InvalidUserDataException> { VersionSelector.parse(value) }.message shouldContain
                  "line must be either 'major.minor.+'"
            }
          }
        },
    )
