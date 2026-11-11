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

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.InvalidUserDataException
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class VersionSelectorTest {

  @Test
  fun `parses latest patch of a line`() {
    val version = VersionSelector.parse("4.0.+")

    assertThat(version).isEqualTo(VersionSelector("4.0.+", VersionLine(4, 0), dynamic = true))
    assertThat(version.taskNameSegment).isEqualTo("4_0")
  }

  @Test
  fun `parses exact version`() {
    val version = VersionSelector.parse(" 4.0.8 ")

    assertThat(version).isEqualTo(VersionSelector("4.0.8", VersionLine(4, 0), dynamic = false))
    assertThat(version.taskNameSegment).isEqualTo("4_0_8")
  }

  @Test
  fun `parses exact version with more segments and qualifiers`() {
    assertThat(VersionSelector.parse("2.18.4.1").taskNameSegment).isEqualTo("2_18_4_1")
    assertThat(VersionSelector.parse("4.2.0-RC1").taskNameSegment).isEqualTo("4_2_0_RC1")
    assertThat(VersionSelector.parse("5.3.39.RELEASE").taskNameSegment).isEqualTo("5_3_39_RELEASE")
    assertThat(VersionSelector.parse("4.2.0-RC1").line).isEqualTo(VersionLine(4, 2))
  }

  @ParameterizedTest
  @ValueSource(strings = ["4", "4.0", "4.+", "4.0.x", "[4.0,4.1)", "latest.release", "+", "", "4.0.+.1", "4.0.1-"])
  fun `rejects versions neither latest patch nor exact`(value: String) {
    assertThatThrownBy { VersionSelector.parse(value) }
        .isInstanceOf(InvalidUserDataException::class.java)
        .hasMessageContaining("line must be either 'major.minor.+'")
  }
}
