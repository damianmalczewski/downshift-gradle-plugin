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

package io.github.malczuuu.downshift

import io.github.malczuuu.downshift.fixtures.TestProject
import io.github.malczuuu.downshift.fixtures.TestProject.Companion.JACKSON_VERSION
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Runs the core plugin scenario, from a Groovy build script, against the Gradle version given by the
 * `compat.gradle.version` system property (`-Pcompat.gradle.version=...` on the build; the CI workflow supplies it from
 * its version matrix). Without the property, the current TestKit Gradle version is used. Run via the `integrationTest`
 * suite. Mirrors [KotlinDslCompatibilityTest] test-for-test, one per DSL.
 */
class GroovyDslCompatibilityTest {

  @TempDir lateinit var projectDir: File

  private val gradleVersion: String? = System.getProperty("compat.gradle.version")

  @Test
  fun `configures every dsl feature and runs every task`() {
    val project = TestProject(projectDir)
    project.writeStandardGroovyBuild(rootProjectName = "compat-under-test")
    project.writeVersionsTest(tag = "jackson2.20+")
    project.appendToGroovyBuildScript(
        """
        downshift {
            platform('jackson') {
                bom = 'com.fasterxml.jackson:jackson-bom'
                lines(['2.19', '2.20'])
            }
            platform('log4j') {
                bom = 'org.apache.logging.log4j:log4j-bom'
                enforced = false
                line('2.25')
            }
            combination('legacyStack') {
                line('jackson', '2.19')
                line('log4j', '2.25')
            }
        }

        println "DOWNSHIFT_TASKS=" + downshift.testTaskNames.get().sort()
        """,
    )

    val runner = project.runner("check")
    gradleVersion?.let(runner::withGradleVersion)
    val result = runner.build()

    assertThat(result.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.output)
        .contains(
            "DOWNSHIFT_TASKS=[testDownshiftJackson2_19, testDownshiftJackson2_20, testDownshiftLegacyStack, " +
                "testDownshiftLog4j2_25]",
        )
    assertThat(result.task(":testDownshiftJackson2_19")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":testDownshiftJackson2_20")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":testDownshiftLog4j2_25")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":testDownshiftLegacyStack")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":testDownshift")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.output).contains("JACKSON=2.19.", "JACKSON=2.20.", "JACKSON=$JACKSON_VERSION", "LOG4J=2.25.")
    // a test tagged jackson2.20+ is skipped only where an older jackson line is used: testDownshiftJackson2_19 and
    // testDownshiftLegacyStack
    assertThat(Regex("TAGGED_RAN").findAll(result.output).count()).isEqualTo(3)
  }
}
