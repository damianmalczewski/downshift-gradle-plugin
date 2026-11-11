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
import io.github.malczuuu.downshift.fixtures.TestProject.Companion.LOG4J_VERSION
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DownshiftPluginFunctionalTest {

  @TempDir lateinit var projectDir: File

  private lateinit var project: TestProject

  @BeforeEach
  fun beforeEach() {
    project = TestProject(projectDir)
    project.writeStandardBuild()
  }

  @Test
  fun `runs tests against the latest patch of a declared line`() {
    project.writeVersionsTest()
    project.appendToBuildScript(
        """
        downshift {
            platform("jackson") {
                bom = "com.fasterxml.jackson:jackson-bom"
                line("2.19.+")
            }
        }
        """,
    )

    val result = project.runner("testDownshiftJackson2_19").build()

    assertThat(result.task(":testDownshiftJackson2_19")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.output).contains("JACKSON=2.19.")
    assertThat(result.output).contains("LOG4J=$LOG4J_VERSION")
  }

  @Test
  fun `runs tests against an exact version`() {
    project.writeVersionsTest()
    project.appendToBuildScript(
        """
        downshift {
            platform("jackson") {
                bom = "com.fasterxml.jackson:jackson-bom"
                line("2.19.2")
            }
        }
        """,
    )

    val result = project.runner("testDownshiftJackson2_19_2").build()

    assertThat(result.task(":testDownshiftJackson2_19_2")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.output).contains("JACKSON=2.19.2")
  }

  @Test
  fun `runs tests against several exact versions of the same line`() {
    project.writeVersionsTest(tag = "jackson2.20+")
    project.appendToBuildScript(
        """
        downshift {
            platform("jackson") {
                bom = "com.fasterxml.jackson:jackson-bom"
                lines("2.19.1", "2.19.2", "2.19.+")
            }
        }
        """,
    )

    val result = project.runner("testDownshift").build()

    assertThat(result.task(":testDownshiftJackson2_19_1")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":testDownshiftJackson2_19_2")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":testDownshiftJackson2_19")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.output).contains("JACKSON=2.19.1", "JACKSON=2.19.2").doesNotContain("TAGGED_RAN")
  }

  @Test
  fun `uses exact pre-release version as declared`() {
    project.writeVersionsTest()
    project.appendToBuildScript(
        """
        downshift {
            platform("jackson") {
                bom = "com.fasterxml.jackson:jackson-bom"
                line("2.19.0-rc2")
            }
        }
        """,
    )

    val result = project.runner("testDownshiftJackson2_19_0_rc2").build()

    assertThat(result.output).contains("JACKSON=2.19.0-rc2")
  }

  @Test
  fun `keeps declared versions in the original test task`() {
    project.writeVersionsTest()
    project.appendToBuildScript(
        """
        downshift {
            platform("jackson") {
                bom = "com.fasterxml.jackson:jackson-bom"
                line("2.19.+")
            }
        }
        """,
    )

    val result = project.runner("test").build()

    assertThat(result.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":testDownshiftJackson2_19")).isNull()
    assertThat(result.output).contains("JACKSON=$JACKSON_VERSION")
  }

  @Test
  fun `does not compile tests again`() {
    project.writeVersionsTest()
    project.appendToBuildScript(
        """
        downshift {
            platform("jackson") {
                bom = "com.fasterxml.jackson:jackson-bom"
                line("2.19.+")
            }
        }
        """,
    )

    val result = project.runner("testDownshiftJackson2_19").build()

    assertThat(result.tasks.map { it.path }).containsOnlyOnce(":compileTestJava")
    assertThat(project.file("build/classes/java").list()).containsExactlyInAnyOrder("test")
  }

  @Test
  fun `skips tests tagged for newer lines`() {
    project.writeVersionsTest(tag = "jackson2.20+")
    project.appendToBuildScript(
        """
        downshift {
            platform("jackson") {
                bom = "com.fasterxml.jackson:jackson-bom"
                lines("2.19.+", "2.20.+")
            }
        }
        """,
    )

    val olderLine = project.runner("testDownshiftJackson2_19").build()
    val taggedLine = project.runner("testDownshiftJackson2_20").build()
    val current = project.runner("test").build()

    assertThat(olderLine.output).contains("JACKSON=2.19.").doesNotContain("TAGGED_RAN")
    assertThat(taggedLine.output).contains("JACKSON=2.20.").contains("TAGGED_RAN")
    assertThat(current.output).contains("TAGGED_RAN")
  }

  @Test
  fun `skips tests tagged for lines between the declared and the current one`() {
    project.writeVersionsTest(tag = "jackson2.21+")
    project.appendToBuildScript(
        """
        downshift {
            platform("jackson") {
                bom = "com.fasterxml.jackson:jackson-bom"
                line("2.19.+")
            }
        }
        """,
    )

    val result = project.runner("testDownshiftJackson2_19").build()

    assertThat(result.output).contains("JACKSON=2.19.").doesNotContain("TAGGED_RAN")
  }

  @Test
  fun `enforced platform overrides versions declared directly`() {
    project.writeVersionsTest()
    project.appendToBuildScript(
        """
        dependencies {
            testImplementation("com.fasterxml.jackson.core:jackson-core:2.21.8")
        }

        downshift {
            platform("jackson") {
                bom = "com.fasterxml.jackson:jackson-bom"
                line("2.19.+")
            }
        }
        """,
    )

    val result = project.runner("testDownshiftJackson2_19").build()

    assertThat(result.output).contains("JACKSON=2.19.")
  }

  @Test
  fun `not enforced platform lets versions declared directly win`() {
    project.writeVersionsTest()
    project.appendToBuildScript(
        """
        dependencies {
            testImplementation("com.fasterxml.jackson.core:jackson-core:2.21.8")
        }

        downshift {
            platform("jackson") {
                bom = "com.fasterxml.jackson:jackson-bom"
                enforced = false
                line("2.19.+")
            }
        }
        """,
    )

    val result = project.runner("testDownshiftJackson2_19").build()

    assertThat(result.output).contains("JACKSON=2.21.8")
  }

  @Test
  fun `combination runs tests against lines of every combined platform`() {
    project.writeVersionsTest(tag = "log4j2.26+")
    project.appendToBuildScript(
        """
        downshift {
            platform("jackson") {
                bom = "com.fasterxml.jackson:jackson-bom"
            }
            platform("log4j") {
                bom = "org.apache.logging.log4j:log4j-bom"
            }
            combination("legacyStack") {
                line("jackson", "2.19.+")
                line("log4j", "2.25.+")
            }
        }
        """,
    )

    val result = project.runner("testDownshiftLegacyStack").build()

    assertThat(result.task(":testDownshiftLegacyStack")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.output).contains("JACKSON=2.19.").contains("LOG4J=2.25.").doesNotContain("TAGGED_RAN")
  }

  @Test
  fun `check runs every line and combination`() {
    project.writeVersionsTest()
    project.appendToBuildScript(
        """
        downshift {
            platform("jackson") {
                bom = "com.fasterxml.jackson:jackson-bom"
                line("2.19.+")
            }
            platform("log4j") {
                bom = "org.apache.logging.log4j:log4j-bom"
                line("2.25.+")
            }
            combination("legacyStack") {
                line("jackson", "2.20.+")
                line("log4j", "2.25.+")
            }
        }
        """,
    )

    val result = project.runner("check").build()

    assertThat(result.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":testDownshiftJackson2_19")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":testDownshiftLog4j2_25")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":testDownshiftLegacyStack")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":testDownshift")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `check does not run downshift tasks when detached`() {
    project.writeVersionsTest()
    project.appendToBuildScript(
        """
        downshift {
            attachToCheck = false
            platform("jackson") {
                bom = "com.fasterxml.jackson:jackson-bom"
                line("2.19.+")
            }
        }
        """,
    )

    val result = project.runner("check").build()

    assertThat(result.task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":testDownshift")).isNull()
    assertThat(result.task(":testDownshiftJackson2_19")).isNull()
  }

  @Test
  fun `runs tests of an opted-in jvm test suite`() {
    project.writeVersionsTest(sourceSet = "integrationTest")
    project.appendToBuildScript(
        """
        testing {
            suites {
                register<JvmTestSuite>("integrationTest") {
                    useJUnitJupiter("${TestProject.JUNIT_VERSION}")
                    dependencies {
                        implementation(platform("com.fasterxml.jackson:jackson-bom:$JACKSON_VERSION"))
                        implementation("com.fasterxml.jackson.core:jackson-core")
                        implementation(platform("org.apache.logging.log4j:log4j-bom:$LOG4J_VERSION"))
                        implementation("org.apache.logging.log4j:log4j-api")
                    }
                }
            }
        }

        downshift {
            sourceSet("integrationTest")
            platform("jackson") {
                bom = "com.fasterxml.jackson:jackson-bom"
                line("2.19.+")
            }
        }
        """,
    )

    val result = project.runner("integrationTestDownshiftJackson2_19").build()

    assertThat(result.task(":integrationTestDownshiftJackson2_19")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.output).contains("JACKSON=2.19.")
  }

  @Test
  fun `reuses the configuration cache`() {
    project.writeVersionsTest()
    project.appendToBuildScript(
        """
        downshift {
            platform("jackson") {
                bom = "com.fasterxml.jackson:jackson-bom"
                line("2.19.+")
            }
        }
        """,
    )

    project.runner("testDownshiftJackson2_19", "--configuration-cache").build()
    val secondRun = project.runner("testDownshiftJackson2_19", "--configuration-cache", "--rerun-tasks").build()

    assertThat(secondRun.output).contains("Configuration cache entry reused")
    assertThat(secondRun.output).contains("JACKSON=2.19.")
  }

  @Test
  fun `works with isolated projects`() {
    project.file("build.gradle.kts").writeText("")
    project.write("settings.gradle.kts", "rootProject.name = \"under-test\"\ninclude(\":first\", \":second\")")
    listOf("first", "second").forEach { name ->
      val subproject = TestProject(project.file(name))
      subproject.writeStandardBuild()
      subproject.file("settings.gradle.kts").delete()
      subproject.file("gradle.properties").delete()
      subproject.writeVersionsTest()
      subproject.appendToBuildScript(
          """
          downshift {
              platform("jackson") {
                  bom = "com.fasterxml.jackson:jackson-bom"
                  line("2.19.+")
              }
          }
          """,
      )
    }

    val result = project.runner("testDownshift", "-Dorg.gradle.unsafe.isolated-projects=true").build()

    assertThat(result.output).contains("Isolated Projects is an incubating feature")
    assertThat(result.task(":first:testDownshiftJackson2_19")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":second:testDownshiftJackson2_19")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.output).contains("JACKSON=2.19.")
  }

  @Test
  fun `uses explicit current line when the bom version cannot be detected`() {
    project.writeVersionsTest(tag = "log4j2.26+")
    project.appendToBuildScript(
        """
        downshift {
            platform("log4j") {
                bom = "org.apache.logging.log4j:log4j-bom"
                currentLine = "2.26"
                line("2.25.+")
            }
        }
        """,
    )
    project
        .file("build.gradle.kts")
        .writeText(
            project
                .file("build.gradle.kts")
                .readText()
                .replace("testImplementation(platform(\"org.apache.logging.log4j:log4j-bom:$LOG4J_VERSION\"))", "")
                .replace(
                    "testImplementation(\"org.apache.logging.log4j:log4j-api\")",
                    "testImplementation(\"org.apache.logging.log4j:log4j-api:$LOG4J_VERSION\")",
                ),
        )

    val result = project.runner("testDownshiftLog4j2_25").build()

    assertThat(result.output).contains("LOG4J=2.25.").doesNotContain("TAGGED_RAN")
  }

  @Test
  fun `fails when the bom version cannot be detected`() {
    project.writeVersionsTest()
    project.appendToBuildScript(
        """
        downshift {
            platform("netty") {
                bom = "io.netty:netty-bom"
                line("4.1.+")
            }
        }
        """,
    )

    val result = project.runner("testDownshiftNetty4_1").buildAndFail()

    assertThat(result.output).contains("downshift: cannot detect which line of io.netty:netty-bom")
  }

  @Test
  fun `fails on platform without bom`() {
    project.appendToBuildScript(
        """
        downshift {
            platform("jackson") {
                line("2.19.+")
            }
        }
        """,
    )

    val result = project.runner("testDownshiftJackson2_19").buildAndFail()

    assertThat(result.output).contains("downshift: platform 'jackson' has no bom")
  }

  @Test
  fun `fails on bom with version`() {
    project.appendToBuildScript(
        """
        downshift {
            platform("jackson") {
                bom = "com.fasterxml.jackson:jackson-bom:2.19.0"
                line("2.19.+")
            }
        }
        """,
    )

    val result = project.runner("testDownshiftJackson2_19").buildAndFail()

    assertThat(result.output).contains("downshift: bom of platform 'jackson' must be in 'group:name' notation")
  }

  @Test
  fun `fails on combination of undeclared platform`() {
    project.appendToBuildScript(
        """
        downshift {
            combination("legacyStack") {
                line("jackson", "2.19.+")
            }
        }
        """,
    )

    val result = project.runner("testDownshiftLegacyStack").buildAndFail()

    assertThat(result.output).contains("downshift: combination 'legacyStack' refers to undeclared platform 'jackson'")
  }

  @Test
  fun `fails on unknown source set`() {
    project.appendToBuildScript(
        """
        downshift {
            sourceSet("integrationTest")
        }
        """,
    )

    val result = project.runner("tasks").buildAndFail()

    assertThat(result.output).contains("downshift: no source set named 'integrationTest'")
  }

  @Test
  fun `fails on line that is neither a latest patch nor an exact version`() {
    project.appendToBuildScript(
        """
        downshift {
            platform("jackson") {
                bom = "com.fasterxml.jackson:jackson-bom"
                line("2.19")
            }
        }
        """,
    )

    val result = project.runner("tasks").buildAndFail()

    assertThat(result.output).contains("downshift: line must be either 'major.minor.+'")
  }
}
