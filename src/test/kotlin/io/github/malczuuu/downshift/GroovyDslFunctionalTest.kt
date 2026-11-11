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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Exercises the `downshift { }` DSL through Groovy's dynamic dispatch rather than the statically bound Kotlin DSL. */
class GroovyDslFunctionalTest {

  @TempDir lateinit var projectDir: File

  private lateinit var project: TestProject

  @BeforeEach
  fun beforeEach() {
    project = TestProject(projectDir)
    project.writeStandardGroovyBuild()
  }

  @Test
  fun `configures every dsl feature and runs every task`() {
    project.writeVersionsTest(tag = "jackson2.20+")
    project.appendToGroovyBuildScript(
        """
        downshift {
            platform('jackson') {
                bom = 'com.fasterxml.jackson:jackson-bom'
                lines('2.19.+', '2.20.+')
            }
            platform('log4j') {
                bom = 'org.apache.logging.log4j:log4j-bom'
                enforced = false
                line('2.25.+')
            }
            combination('legacyStack') {
                line('jackson', '2.19.+')
                line('log4j', '2.25.+')
            }
        }
        """,
    )

    val result = project.runner("check").build()

    assertThat(result.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
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

  @Test
  fun `configures source set and detaches from check`() {
    project.writeVersionsTest()
    project.appendToGroovyBuildScript(
        """
        sourceSets {
            integrationTest
        }

        downshift {
            attachToCheck = false
            sourceSet('integrationTest')
        }
        """,
    )

    val result = project.runner("check").build()

    assertThat(result.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":testDownshift")).isNull()
    assertThat(result.task(":integrationTestDownshift")).isNull()
  }
}
