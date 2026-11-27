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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test as TestTask
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.api.tasks.testing.testng.TestNGOptions
import org.gradle.kotlin.dsl.register
import org.gradle.testfixtures.ProjectBuilder

class TestTaskMirrorSpec :
    FunSpec(
        {
          lateinit var project: Project
          lateinit var base: TestTask
          lateinit var variant: TestTask

          beforeEach {
            project = ProjectBuilder.builder().build()
            project.plugins.apply("java")
            base = project.tasks.getByName("test") as TestTask
            variant = project.tasks.register<TestTask>("variant").get()
          }

          test("copies settings of the base test task") {
            base.jvmArgs("-Xshared", "-Xbase")
            base.systemProperty("shared", "base")
            base.systemProperty("base.only", "yes")
            base.environment("BASE_ONLY", "yes")
            base.include("**/*Test.class")
            base.exclude("**/Slow*")
            base.filter.includeTestsMatching("com.acme.*")
            base.filter.excludeTestsMatching("*Flaky*")
            base.filter.isFailOnNoMatchingTests = false
            base.maxParallelForks = 3
            base.forkEvery = 10
            base.minHeapSize = "64m"
            base.maxHeapSize = "512m"
            base.failFast = true
            base.ignoreFailures = true
            variant.jvmArgs("-Xshared")
            variant.systemProperty("shared", "variant")

            mirrorTestTask(base, variant, emptyList())

            variant.jvmArgs!! shouldContainExactly listOf("-Xshared", "-Xbase")
            variant.systemProperties.shouldContain("shared" to "variant")
            variant.systemProperties.shouldContain("base.only" to "yes")
            variant.environment.shouldContain("BASE_ONLY" to "yes")
            variant.includes shouldContainExactly listOf("**/*Test.class")
            variant.excludes shouldContainExactly listOf("**/Slow*")
            variant.filter.includePatterns shouldContainExactly listOf("com.acme.*")
            variant.filter.excludePatterns shouldContainExactly listOf("*Flaky*")
            variant.filter.isFailOnNoMatchingTests shouldBe false
            variant.maxParallelForks shouldBe 3
            variant.forkEvery shouldBe 10
            variant.minHeapSize shouldBe "64m"
            variant.maxHeapSize shouldBe "512m"
            variant.failFast shouldBe true
            variant.ignoreFailures shouldBe true
          }

          test("keeps settings of the variant") {
            base.maxParallelForks = 3
            base.maxHeapSize = "512m"
            variant.maxParallelForks = 2
            variant.maxHeapSize = "1g"

            mirrorTestTask(base, variant, emptyList())

            variant.maxParallelForks shouldBe 2
            variant.maxHeapSize shouldBe "1g"
          }

          test("does not duplicate settings applied to both tasks") {
            listOf(base, variant).forEach {
              it.jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
              it.useJUnitPlatform { excludeTags("slow") }
            }

            mirrorTestTask(base, variant, listOf("acme1.1+"))

            variant.jvmArgs!! shouldContainExactly listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
            (variant.options as JUnitPlatformOptions).excludeTags shouldContainExactlyInAnyOrder
                setOf("slow", "acme1.1+")
          }

          test("copies junit platform options and excludes tags") {
            base.useJUnitPlatform {
              includeTags("fast")
              excludeTags("slow")
              includeEngines("junit-jupiter")
              excludeEngines("junit-vintage")
            }

            val applied = mirrorTestTask(base, variant, listOf("acme1.1+", "acme1.2+"))

            val options = variant.options as JUnitPlatformOptions
            applied shouldBe true
            options.includeTags shouldContainExactly setOf("fast")
            options.excludeTags shouldContainExactlyInAnyOrder setOf("slow", "acme1.1+", "acme1.2+")
            options.includeEngines shouldContainExactly setOf("junit-jupiter")
            options.excludeEngines shouldContainExactly setOf("junit-vintage")
          }

          test("excludes tags as testng groups") {
            base.useTestNG { excludeGroups("slow") }

            val applied = mirrorTestTask(base, variant, listOf("acme1.1+"))

            applied shouldBe true
            (variant.options as TestNGOptions).excludeGroups shouldContainExactlyInAnyOrder setOf("slow", "acme1.1+")
          }

          test("reports tags not applied on junit 4") {
            base.useJUnit()

            mirrorTestTask(base, variant, listOf("acme1.1+")) shouldBe false
          }

          test("reports nothing to apply on junit 4 without tags") {
            base.useJUnit()

            mirrorTestTask(base, variant, emptyList()) shouldBe true
          }

          test("merges arguments counting repeated ones") {
            val baseArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED", "-Xbase")
            val own =
                listOf(
                    "--add-opens",
                    "java.base/java.lang=ALL-UNNAMED",
                    "--add-opens",
                    "java.base/java.util=ALL-UNNAMED",
                )

            mergeArguments(baseArgs, own) shouldContainExactly
                listOf(
                    "--add-opens",
                    "java.base/java.lang=ALL-UNNAMED",
                    "-Xbase",
                    "--add-opens",
                    "java.base/java.util=ALL-UNNAMED",
                )
          }
        },
    )
