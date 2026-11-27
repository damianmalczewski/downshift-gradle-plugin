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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.api.InvalidUserDataException

class VersionLineSpec :
    FunSpec(
        {
          test("parses major-minor line") {
            VersionLine.parse("4.0") shouldBe VersionLine(4, 0)
            VersionLine.parse(" 2.19 ") shouldBe VersionLine(2, 19)
          }

          listOf("4", "4.0.1", "4.x", "", "a.b", "4.0+").forEach { value ->
            test("rejects line '$value' not in major-minor format") {
              shouldThrow<InvalidUserDataException> { VersionLine.parse(value) }.message shouldContain
                  "currentLine must be in 'major.minor' format"
            }
          }

          test("extracts line of a version") {
            VersionLine.ofVersion("4.1.2") shouldBe VersionLine(4, 1)
            VersionLine.ofVersion("4.1") shouldBe VersionLine(4, 1)
            VersionLine.ofVersion("4.1.+") shouldBe VersionLine(4, 1)
            VersionLine.ofVersion("2.18.4.1") shouldBe VersionLine(2, 18)
            VersionLine.ofVersion("4.2.0-RC1") shouldBe VersionLine(4, 2)
          }

          listOf("4", "[4.0,5.0)", "latest.release", "+").forEach { value ->
            test("returns null for version '$value' without major-minor prefix") {
              VersionLine.ofVersion(value) shouldBe null
            }
          }

          test("orders by major then minor") {
            listOf(VersionLine(4, 10), VersionLine(3, 5), VersionLine(4, 2)).sorted() shouldContainExactly
                listOf(VersionLine(3, 5), VersionLine(4, 2), VersionLine(4, 10))
          }
        },
    )
