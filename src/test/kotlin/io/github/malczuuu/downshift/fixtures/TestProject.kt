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

package io.github.malczuuu.downshift.fixtures

import java.io.File
import org.gradle.testkit.runner.GradleRunner

/**
 * Fixture around a temporary Gradle project directory, shared by functional and integration tests. Provides file
 * helpers, the standard sample project (JUnit tests printing versions of Jackson and Log4j found at runtime) and a
 * preconfigured [GradleRunner].
 */
class TestProject(val dir: File) {

  fun file(relativePath: String): File = File(dir, relativePath)

  fun write(relativePath: String, content: String): File =
      file(relativePath).apply {
        parentFile.mkdirs()
        writeText(content.trimIndent() + "\n")
      }

  fun writeTestSource(relativePath: String, content: String, sourceSet: String = "test"): File =
      write("src/$sourceSet/java/$relativePath", content)

  fun appendToBuildScript(content: String) {
    file("build.gradle.kts").appendText("\n" + content.trimIndent() + "\n")
  }

  fun appendToGroovyBuildScript(content: String) {
    file("build.gradle").appendText("\n" + content.trimIndent() + "\n")
  }

  /**
   * Writes `settings.gradle.kts` and a `build.gradle.kts` applying `java` and the plugin under test, with JUnit tests
   * depending on Jackson ([JACKSON_VERSION]) and Log4j ([LOG4J_VERSION]) through their BOMs.
   */
  fun writeStandardBuild(rootProjectName: String = "under-test") {
    write("settings.gradle.kts", "rootProject.name = \"$rootProjectName\"")
    write(
        "build.gradle.kts",
        """
        plugins {
            java
            id("io.github.malczuuu.downshift")
        }

        repositories {
            mavenCentral()
        }

        dependencies {
            testImplementation(platform("org.junit:junit-bom:$JUNIT_VERSION"))
            testImplementation("org.junit.jupiter:junit-jupiter")
            testRuntimeOnly("org.junit.platform:junit-platform-launcher")

            testImplementation(platform("com.fasterxml.jackson:jackson-bom:$JACKSON_VERSION"))
            testImplementation("com.fasterxml.jackson.core:jackson-core")
            testImplementation(platform("org.apache.logging.log4j:log4j-bom:$LOG4J_VERSION"))
            testImplementation("org.apache.logging.log4j:log4j-api")
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                showStandardStreams = true
            }
        }
        """,
    )
    writeTestKitGradleProperties()
  }

  /** Groovy counterpart of [writeStandardBuild]. */
  fun writeStandardGroovyBuild(rootProjectName: String = "under-test") {
    write("settings.gradle", "rootProject.name = '$rootProjectName'")
    write(
        "build.gradle",
        """
        plugins {
            id 'java'
            id 'io.github.malczuuu.downshift'
        }

        repositories {
            mavenCentral()
        }

        dependencies {
            testImplementation platform('org.junit:junit-bom:$JUNIT_VERSION')
            testImplementation 'org.junit.jupiter:junit-jupiter'
            testRuntimeOnly 'org.junit.platform:junit-platform-launcher'

            testImplementation platform('com.fasterxml.jackson:jackson-bom:$JACKSON_VERSION')
            testImplementation 'com.fasterxml.jackson.core:jackson-core'
            testImplementation platform('org.apache.logging.log4j:log4j-bom:$LOG4J_VERSION')
            testImplementation 'org.apache.logging.log4j:log4j-api'
        }

        tasks.withType(Test).configureEach {
            useJUnitPlatform()
            testLogging {
                showStandardStreams = true
            }
        }
        """,
    )
    writeTestKitGradleProperties()
  }

  // Keep TestKit daemons small and short-lived; the compatibility matrix spawns one daemon per
  // Gradle version and accumulating full-size idle daemons destabilizes the test run.
  private fun writeTestKitGradleProperties() {
    write(
        "gradle.properties",
        """
        org.gradle.jvmargs=-Xmx512m
        org.gradle.daemon.idletimeout=10000
        """,
    )
  }

  /**
   * Writes a test printing `JACKSON=<version>` and `LOG4J=<version>` found at runtime and `MIRRORED=<value>` of the
   * `mirrored.property` system property, and a test tagged [tag] (if given) printing `TAGGED_RAN`.
   */
  fun writeVersionsTest(sourceSet: String = "test", tag: String? = null) {
    writeTestSource(
        "com/acme/VersionsTest.java",
        """
        package com.acme;

        import org.junit.jupiter.api.Tag;
        import org.junit.jupiter.api.Test;

        class VersionsTest {

            @Test
            void printsVersions() {
                System.out.println("JACKSON=" + com.fasterxml.jackson.core.json.PackageVersion.VERSION);
                System.out.println("LOG4J=" + org.apache.logging.log4j.LogManager.class.getPackage().getImplementationVersion());
                System.out.println("MIRRORED=" + System.getProperty("mirrored.property"));
            }
            ${if (tag == null) "" else "@Tag(\"$tag\")"}
            @Test
            void tagged() {
                System.out.println("${if (tag == null) "UNTAGGED_RAN" else "TAGGED_RAN"}");
            }
        }
        """,
        sourceSet,
    )
  }

  fun runner(vararg arguments: String): GradleRunner =
      GradleRunner.create().withProjectDir(dir).withPluginClasspath().withArguments(*arguments)

  companion object {
    const val JUNIT_VERSION = "5.13.4"
    const val JACKSON_VERSION = "2.21.7"
    const val LOG4J_VERSION = "2.26.1"
  }
}
