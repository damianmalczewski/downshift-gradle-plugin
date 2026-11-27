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

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.util.GradleVersion

class DownshiftCommonsSpec :
    FunSpec(
        {
          test("accepts minimum gradle version") {
            shouldNotThrowAny { requireMinimumGradleVersion(GradleVersion.version(MINIMUM_GRADLE_VERSION)) }
          }

          test("rejects gradle older than minimum") {
            shouldThrow<GradleException> { requireMinimumGradleVersion(GradleVersion.version("8.3")) }
                .message shouldContain "requires Gradle $MINIMUM_GRADLE_VERSION or later"
          }

          listOf("springBoot", "kotlin", "log4j2", "A").forEach { name ->
            test("accepts alphanumeric name '$name'") { shouldNotThrowAny { requireValidName("platform", name) } }
          }

          listOf("", "spring-boot", "spring.boot", "2kotlin", "spring boot", "boot_4").forEach { name ->
            test("rejects name '$name' unfit for tags and task names") {
              shouldThrow<InvalidUserDataException> { requireValidName("platform", name) }.message shouldContain
                  "platform name must be alphanumeric"
            }
          }

          listOf("springBoot", "log4j").forEach { name ->
            test("accepts platform name '$name'") { shouldNotThrowAny { requireValidPlatformName(name) } }
          }

          test("rejects platform names ending with a digit") {
            val message = shouldThrow<InvalidUserDataException> { requireValidPlatformName("log4j2") }.message

            message shouldContain "platform name must not end with a digit"
            message shouldContain "\"log4j\""
          }

          test("rejects platform names unfit for tags and task names") {
            shouldThrow<InvalidUserDataException> { requireValidPlatformName("spring-boot") }.message shouldContain
                "platform name must be alphanumeric"
          }

          listOf(
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
              )
              .forEach { version ->
                test("accepts released version '$version'") { isStableVersion(version) shouldBe true }
              }

          listOf(
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
              )
              .forEach { version ->
                test("rejects pre-release version '$version'") { isStableVersion(version) shouldBe false }
              }

          test("capitalizes first character only") { "springBoot".capitalized() shouldBe "SpringBoot" }
        },
    )
