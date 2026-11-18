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
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test as TestTask
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.api.tasks.testing.testng.TestNGOptions
import org.gradle.kotlin.dsl.register
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TestTaskMirrorTest {

  private lateinit var project: Project
  private lateinit var base: TestTask
  private lateinit var variant: TestTask

  @BeforeEach
  fun beforeEach() {
    project = ProjectBuilder.builder().build()
    project.plugins.apply("java")
    base = project.tasks.getByName("test") as TestTask
    variant = project.tasks.register<TestTask>("variant").get()
  }

  @Test
  fun `copies settings of the base test task`() {
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

    assertThat(variant.jvmArgs).containsExactly("-Xshared", "-Xbase")
    assertThat(variant.systemProperties).containsEntry("shared", "variant").containsEntry("base.only", "yes")
    assertThat(variant.environment).containsEntry("BASE_ONLY", "yes")
    assertThat(variant.includes).containsExactly("**/*Test.class")
    assertThat(variant.excludes).containsExactly("**/Slow*")
    assertThat(variant.filter.includePatterns).containsExactly("com.acme.*")
    assertThat(variant.filter.excludePatterns).containsExactly("*Flaky*")
    assertThat(variant.filter.isFailOnNoMatchingTests).isFalse()
    assertThat(variant.maxParallelForks).isEqualTo(3)
    assertThat(variant.forkEvery).isEqualTo(10)
    assertThat(variant.minHeapSize).isEqualTo("64m")
    assertThat(variant.maxHeapSize).isEqualTo("512m")
    assertThat(variant.failFast).isTrue()
    assertThat(variant.ignoreFailures).isTrue()
  }

  @Test
  fun `keeps settings of the variant`() {
    base.maxParallelForks = 3
    base.maxHeapSize = "512m"
    variant.maxParallelForks = 2
    variant.maxHeapSize = "1g"

    mirrorTestTask(base, variant, emptyList())

    assertThat(variant.maxParallelForks).isEqualTo(2)
    assertThat(variant.maxHeapSize).isEqualTo("1g")
  }

  @Test
  fun `does not duplicate settings applied to both tasks`() {
    listOf(base, variant).forEach {
      it.jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
      it.useJUnitPlatform { excludeTags("slow") }
    }

    mirrorTestTask(base, variant, listOf("acme1.1+"))

    assertThat(variant.jvmArgs).containsExactly("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    assertThat((variant.options as JUnitPlatformOptions).excludeTags).containsExactlyInAnyOrder("slow", "acme1.1+")
  }

  @Test
  fun `copies junit platform options and excludes tags`() {
    base.useJUnitPlatform {
      includeTags("fast")
      excludeTags("slow")
      includeEngines("junit-jupiter")
      excludeEngines("junit-vintage")
    }

    val applied = mirrorTestTask(base, variant, listOf("acme1.1+", "acme1.2+"))

    val options = variant.options as JUnitPlatformOptions
    assertThat(applied).isTrue()
    assertThat(options.includeTags).containsExactly("fast")
    assertThat(options.excludeTags).containsExactlyInAnyOrder("slow", "acme1.1+", "acme1.2+")
    assertThat(options.includeEngines).containsExactly("junit-jupiter")
    assertThat(options.excludeEngines).containsExactly("junit-vintage")
  }

  @Test
  fun `excludes tags as testng groups`() {
    base.useTestNG { excludeGroups("slow") }

    val applied = mirrorTestTask(base, variant, listOf("acme1.1+"))

    assertThat(applied).isTrue()
    assertThat((variant.options as TestNGOptions).excludeGroups).containsExactlyInAnyOrder("slow", "acme1.1+")
  }

  @Test
  fun `reports tags not applied on junit 4`() {
    base.useJUnit()

    assertThat(mirrorTestTask(base, variant, listOf("acme1.1+"))).isFalse()
  }

  @Test
  fun `reports nothing to apply on junit 4 without tags`() {
    base.useJUnit()

    assertThat(mirrorTestTask(base, variant, emptyList())).isTrue()
  }

  @Test
  fun `merges arguments counting repeated ones`() {
    val base = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED", "-Xbase")
    val own = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED", "--add-opens", "java.base/java.util=ALL-UNNAMED")

    assertThat(mergeArguments(base, own))
        .containsExactly(
            "--add-opens",
            "java.base/java.lang=ALL-UNNAMED",
            "-Xbase",
            "--add-opens",
            "java.base/java.util=ALL-UNNAMED",
        )
  }
}
