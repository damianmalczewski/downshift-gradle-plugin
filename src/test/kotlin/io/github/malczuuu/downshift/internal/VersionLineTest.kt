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

class VersionLineTest {

  @Test
  fun `parses major-minor line`() {
    assertThat(VersionLine.parse("4.0")).isEqualTo(VersionLine(4, 0))
    assertThat(VersionLine.parse(" 2.19 ")).isEqualTo(VersionLine(2, 19))
  }

  @ParameterizedTest
  @ValueSource(strings = ["4", "4.0.1", "4.x", "", "a.b", "4.0+"])
  fun `rejects lines not in major-minor format`(value: String) {
    assertThatThrownBy { VersionLine.parse(value) }
        .isInstanceOf(InvalidUserDataException::class.java)
        .hasMessageContaining("currentLine must be in 'major.minor' format")
  }

  @Test
  fun `extracts line of a version`() {
    assertThat(VersionLine.ofVersion("4.1.2")).isEqualTo(VersionLine(4, 1))
    assertThat(VersionLine.ofVersion("4.1")).isEqualTo(VersionLine(4, 1))
    assertThat(VersionLine.ofVersion("4.1.+")).isEqualTo(VersionLine(4, 1))
    assertThat(VersionLine.ofVersion("2.18.4.1")).isEqualTo(VersionLine(2, 18))
    assertThat(VersionLine.ofVersion("4.2.0-RC1")).isEqualTo(VersionLine(4, 2))
  }

  @ParameterizedTest
  @ValueSource(strings = ["4", "[4.0,5.0)", "latest.release", "+"])
  fun `returns null for versions without major-minor prefix`(value: String) {
    assertThat(VersionLine.ofVersion(value)).isNull()
  }

  @Test
  fun `orders by major then minor`() {
    assertThat(listOf(VersionLine(4, 10), VersionLine(3, 5), VersionLine(4, 2)).sorted())
        .containsExactly(VersionLine(3, 5), VersionLine(4, 2), VersionLine(4, 10))
  }
}
