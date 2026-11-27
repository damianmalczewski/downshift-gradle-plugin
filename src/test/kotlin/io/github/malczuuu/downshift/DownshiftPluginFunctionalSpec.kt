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
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.gradle.testkit.runner.TaskOutcome

class DownshiftPluginFunctionalSpec :
    FunSpec(
        {
          lateinit var project: TestProject

          beforeEach {
            project = TestProject(tempdir())
            project.writeStandardBuild()
          }

          test("runs tests against the latest patch of a declared line") {
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

            result.task(":testDownshiftJackson2_19")?.outcome shouldBe TaskOutcome.SUCCESS
            result.output shouldContain "JACKSON=2.19."
            result.output shouldContain "LOG4J=$LOG4J_VERSION"
          }

          test("runs tests against an exact version") {
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

            result.task(":testDownshiftJackson2_19_2")?.outcome shouldBe TaskOutcome.SUCCESS
            result.output shouldContain "JACKSON=2.19.2"
          }

          test("runs tests against several exact versions of the same line") {
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

            result.task(":testDownshiftJackson2_19_1")?.outcome shouldBe TaskOutcome.SUCCESS
            result.task(":testDownshiftJackson2_19_2")?.outcome shouldBe TaskOutcome.SUCCESS
            result.task(":testDownshiftJackson2_19")?.outcome shouldBe TaskOutcome.SUCCESS
            result.output shouldContain "JACKSON=2.19.1"
            result.output shouldContain "JACKSON=2.19.2"
            result.output shouldNotContain "TAGGED_RAN"
          }

          test("uses exact pre-release version as declared") {
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

            result.output shouldContain "JACKSON=2.19.0-rc2"
          }

          test("keeps declared versions in the original test task") {
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

            result.task(":test")?.outcome shouldBe TaskOutcome.SUCCESS
            result.task(":testDownshiftJackson2_19") shouldBe null
            result.output shouldContain "JACKSON=$JACKSON_VERSION"
          }

          test("does not compile tests again") {
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

            result.tasks.map { it.path }.count { it == ":compileTestJava" } shouldBe 1
            project.file("build/classes/java").list()!!.toList() shouldContainExactlyInAnyOrder listOf("test")
          }

          test("skips tests tagged for newer lines") {
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

            olderLine.output shouldContain "JACKSON=2.19."
            olderLine.output shouldNotContain "TAGGED_RAN"
            taggedLine.output shouldContain "JACKSON=2.20."
            taggedLine.output shouldContain "TAGGED_RAN"
            current.output shouldContain "TAGGED_RAN"
          }

          test("skips tests tagged for lines between the declared and the current one") {
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

            result.output shouldContain "JACKSON=2.19."
            result.output shouldNotContain "TAGGED_RAN"
          }

          test("enforced platform overrides versions declared directly") {
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

            result.output shouldContain "JACKSON=2.19."
          }

          test("not enforced platform lets versions declared directly win") {
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

            result.output shouldContain "JACKSON=2.21.8"
          }

          test("combination runs tests against lines of every combined platform") {
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

            result.task(":testDownshiftLegacyStack")?.outcome shouldBe TaskOutcome.SUCCESS
            result.output shouldContain "JACKSON=2.19."
            result.output shouldContain "LOG4J=2.25."
            result.output shouldNotContain "TAGGED_RAN"
          }

          test("check runs every line and combination") {
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

            result.task(":test")?.outcome shouldBe TaskOutcome.SUCCESS
            result.task(":testDownshiftJackson2_19")?.outcome shouldBe TaskOutcome.SUCCESS
            result.task(":testDownshiftLog4j2_25")?.outcome shouldBe TaskOutcome.SUCCESS
            result.task(":testDownshiftLegacyStack")?.outcome shouldBe TaskOutcome.SUCCESS
            result.task(":testDownshift")?.outcome shouldBe TaskOutcome.SUCCESS
          }

          test("check does not run downshift tasks when detached") {
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

            result.task(":test")?.outcome shouldBe TaskOutcome.SUCCESS
            result.task(":testDownshift") shouldBe null
            result.task(":testDownshiftJackson2_19") shouldBe null
          }

          test("runs tests of an opted-in jvm test suite") {
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

            result.task(":integrationTestDownshiftJackson2_19")?.outcome shouldBe TaskOutcome.SUCCESS
            result.output shouldContain "JACKSON=2.19."
          }

          test("copies settings of the test task") {
            project.writeVersionsTest(tag = "slow")
            project.appendToBuildScript(
                """
        tasks.test {
            systemProperty("mirrored.property", "yes")
            useJUnitPlatform {
                excludeTags("slow")
            }
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

            result.output shouldContain "JACKSON=2.19."
            result.output shouldContain "MIRRORED=yes"
            result.output shouldNotContain "TAGGED_RAN"
          }

          test("fails when versions of an enforced platform are overridden") {
            project.writeVersionsTest()
            project.appendToBuildScript(
                """
        configurations.configureEach {
            resolutionStrategy.eachDependency {
                if (requested.group == "com.fasterxml.jackson.core") {
                    useVersion("$JACKSON_VERSION")
                }
            }
        }

        downshift {
            platform("jackson") {
                bom = "com.fasterxml.jackson:jackson-bom"
                line("2.19.+")
            }
        }
        """,
            )

            val result = project.runner("testDownshiftJackson2_19").buildAndFail()

            result.output shouldContain
                "task ':testDownshiftJackson2_19' must run against com.fasterxml.jackson:jackson-bom:2.19.+"
            result.output shouldContain "com.fasterxml.jackson.core:jackson-core:$JACKSON_VERSION instead of 2.19."
            result.output shouldNotContain "JACKSON="
          }

          test("fails when spring dependency management overrides versions of an enforced platform") {
            project.writeVersionsTest()
            project.write(
                "build.gradle.kts",
                """
        plugins {
            java
            id("io.spring.dependency-management") version "1.1.7"
            id("io.github.malczuuu.downshift")
        }

        repositories {
            mavenCentral()
        }

        dependencyManagement {
            imports {
                mavenBom("com.fasterxml.jackson:jackson-bom:$JACKSON_VERSION")
                mavenBom("org.apache.logging.log4j:log4j-bom:$LOG4J_VERSION")
                mavenBom("org.junit:junit-bom:${TestProject.JUNIT_VERSION}")
            }
        }

        dependencies {
            testImplementation("org.junit.jupiter:junit-jupiter")
            testRuntimeOnly("org.junit.platform:junit-platform-launcher")
            testImplementation("com.fasterxml.jackson.core:jackson-core")
            testImplementation("org.apache.logging.log4j:log4j-api")
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }

        downshift {
            platform("jackson") {
                bom = "com.fasterxml.jackson:jackson-bom"
                currentLine = "${JACKSON_VERSION.substringBeforeLast('.')}"
                line("2.19.+")
            }
        }
        """,
            )

            val result = project.runner("testDownshiftJackson2_19").buildAndFail()

            result.output shouldContain "com.fasterxml.jackson.core:jackson-core:$JACKSON_VERSION instead of 2.19."
            result.output shouldContain "io.spring.dependency-management"
          }

          test("reuses the configuration cache") {
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

            secondRun.output shouldContain "Configuration cache entry reused"
            secondRun.output shouldContain "JACKSON=2.19."
          }

          test("works with isolated projects") {
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

            result.output shouldContain "Isolated Projects is an incubating feature"
            result.task(":first:testDownshiftJackson2_19")?.outcome shouldBe TaskOutcome.SUCCESS
            result.task(":second:testDownshiftJackson2_19")?.outcome shouldBe TaskOutcome.SUCCESS
            result.output shouldContain "JACKSON=2.19."
          }

          test("uses explicit current line when the bom version cannot be detected") {
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
                        .replace(
                            "testImplementation(platform(\"org.apache.logging.log4j:log4j-bom:$LOG4J_VERSION\"))",
                            "",
                        )
                        .replace(
                            "testImplementation(\"org.apache.logging.log4j:log4j-api\")",
                            "testImplementation(\"org.apache.logging.log4j:log4j-api:$LOG4J_VERSION\")",
                        ),
                )

            val result = project.runner("testDownshiftLog4j2_25").build()

            result.output shouldContain "LOG4J=2.25."
            result.output shouldNotContain "TAGGED_RAN"
          }

          test("fails when the bom version cannot be detected") {
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

            result.output shouldContain "downshift: cannot detect which line of io.netty:netty-bom"
          }

          test("fails on platform without bom") {
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

            result.output shouldContain "downshift: platform 'jackson' has no bom"
          }

          test("fails on bom with version") {
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

            result.output shouldContain "downshift: bom of platform 'jackson' must be in 'group:name' notation"
          }

          test("fails on combination of undeclared platform") {
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

            result.output shouldContain "downshift: combination 'legacyStack' refers to undeclared platform 'jackson'"
          }

          test("fails on unknown source set") {
            project.appendToBuildScript(
                """
        downshift {
            sourceSet("integrationTest")
        }
        """,
            )

            val result = project.runner("tasks").buildAndFail()

            result.output shouldContain "downshift: no source set named 'integrationTest'"
          }

          test("fails on line that is neither a latest patch nor an exact version") {
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

            result.output shouldContain "downshift: line must be either 'major.minor.+'"
          }
        },
    )
