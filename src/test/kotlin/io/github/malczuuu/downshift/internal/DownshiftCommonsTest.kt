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
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DownshiftCommonsTest {

  @Test
  fun `accepts minimum gradle version`() {
    assertThatCode { requireMinimumGradleVersion(GradleVersion.version(MINIMUM_GRADLE_VERSION)) }
        .doesNotThrowAnyException()
  }

  @Test
  fun `rejects gradle older than minimum`() {
    assertThatThrownBy { requireMinimumGradleVersion(GradleVersion.version("8.3")) }
        .isInstanceOf(GradleException::class.java)
        .hasMessageContaining("requires Gradle $MINIMUM_GRADLE_VERSION or later")
  }

  @ParameterizedTest
  @ValueSource(strings = ["springBoot", "kotlin", "log4j2", "A"])
  fun `accepts alphanumeric names`(name: String) {
    assertThatCode { requireValidName("platform", name) }.doesNotThrowAnyException()
  }

  @ParameterizedTest
  @ValueSource(strings = ["", "spring-boot", "spring.boot", "2kotlin", "spring boot", "boot_4"])
  fun `rejects names unfit for tags and task names`(name: String) {
    assertThatThrownBy { requireValidName("platform", name) }
        .isInstanceOf(InvalidUserDataException::class.java)
        .hasMessageContaining("platform name must be alphanumeric")
  }

  @ParameterizedTest
  @ValueSource(strings = ["springBoot", "log4j"])
  fun `accepts platform names`(name: String) {
    assertThatCode { requireValidPlatformName(name) }.doesNotThrowAnyException()
  }

  @Test
  fun `rejects platform names ending with a digit`() {
    assertThatThrownBy { requireValidPlatformName("log4j2") }
        .isInstanceOf(InvalidUserDataException::class.java)
        .hasMessageContaining("platform name must not end with a digit")
        .hasMessageContaining("\"log4j\"")
  }

  @Test
  fun `rejects platform names unfit for tags and task names`() {
    assertThatThrownBy { requireValidPlatformName("spring-boot") }
        .isInstanceOf(InvalidUserDataException::class.java)
        .hasMessageContaining("platform name must be alphanumeric")
  }

  @ParameterizedTest
  @ValueSource(
      strings =
          [
              "4.0.9",
              "2.18.4.1",
              "33",
              "5.3.39.RELEASE",
              "6.6.1.Final",
              "33.4.8-jre",
              "33.4.8-android",
              "4.1.115.Final",
              "2.0.21-release-1",
              "1.0.0-GA",
          ],
  )
  fun `accepts released versions`(version: String) {
    assertThat(isStableVersion(version)).isTrue()
  }

  @ParameterizedTest
  @ValueSource(
      strings =
          [
              "4.2.0-RC1",
              "4.2.0-M2",
              "2.19.0-rc1",
              "2.3.0-Beta1",
              "4.1.3-SNAPSHOT",
              "5.3.0.M1",
              "5.3.0.RC2",
              "7.0.0.CR1",
              "1.0.0-alpha.2",
              "2.2.0-dev-5873",
              "3.0.0-preview1",
              "2.0.0b3",
          ],
  )
  fun `rejects pre-release versions`(version: String) {
    assertThat(isStableVersion(version)).isFalse()
  }

  @Test
  fun `capitalizes first character only`() {
    assertThat("springBoot".capitalized()).isEqualTo("SpringBoot")
  }
}
