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

import io.github.malczuuu.downshift.dsl.DownshiftExtension
import io.github.malczuuu.downshift.fixtures.LocalMavenRepository
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.tasks.testing.Test as TestTask
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.kotlin.dsl.getByType
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Resolves runtime classpaths registered by the plugin against a local Maven repository, offline. Complements the
 * TestKit functional tests by covering resolution rules in-process, where they are measured by coverage.
 */
class DownshiftResolutionTest {

  @TempDir lateinit var tempDir: File

  private lateinit var project: Project

  @BeforeEach
  fun beforeEach() {
    val repository = LocalMavenRepository(File(tempDir, "repository"))
    listOf("1.0.0", "1.0.1", "1.1.0", "1.1.1-RC1", "1.2.0").forEach { version ->
      repository.library("org.acme", "acme-lib", version)
      repository.bom("org.acme", "acme-bom", version, mapOf("org.acme:acme-lib" to version))
    }
    listOf("2.0.0", "2.1.0").forEach { version ->
      repository.library("org.other", "other-lib", version)
      repository.bom("org.other", "other-bom", version, mapOf("org.other:other-lib" to version))
    }

    project = ProjectBuilder.builder().withProjectDir(File(tempDir, "project")).build()
    project.plugins.apply("java")
    project.plugins.apply("io.github.malczuuu.downshift")
    project.repositories.maven { url = repository.dir.toURI() }
    project.dependencies.apply {
      add("testImplementation", platform("org.acme:acme-bom:1.2.0"))
      add("testImplementation", "org.acme:acme-lib")
      add("testImplementation", platform("org.other:other-bom:2.1.0"))
      add("testImplementation", "org.other:other-lib")
    }
  }

  private fun extension(): DownshiftExtension = project.extensions.getByType()

  private fun acme(vararg lines: String, enforced: Boolean = true) {
    extension().platform("acme") {
      bom.set("org.acme:acme-bom")
      this.enforced.set(enforced)
      lines(*lines)
    }
  }

  private fun resolvedVersion(configurationName: String, module: String): String? =
      project.configurations
          .getByName(configurationName)
          .incoming
          .resolutionResult
          .allComponents
          .map { it.id }
          .filterIsInstance<ModuleComponentIdentifier>()
          .firstOrNull { "${it.group}:${it.module}" == module }
          ?.version

  @Test
  fun `keeps declared versions on the original runtime classpath`() {
    acme("1.0.+")

    assertThat(resolvedVersion("testRuntimeClasspath", "org.acme:acme-lib")).isEqualTo("1.2.0")
  }

  @Test
  fun `resolves the latest patch of a line`() {
    acme("1.0.+")

    assertThat(resolvedVersion("testDownshiftAcme1_0RuntimeClasspath", "org.acme:acme-bom")).isEqualTo("1.0.1")
    assertThat(resolvedVersion("testDownshiftAcme1_0RuntimeClasspath", "org.acme:acme-lib")).isEqualTo("1.0.1")
    assertThat(resolvedVersion("testDownshiftAcme1_0RuntimeClasspath", "org.other:other-lib")).isEqualTo("2.1.0")
  }

  @Test
  fun `skips pre-releases when resolving the latest patch of a line`() {
    acme("1.1.+")

    assertThat(resolvedVersion("testDownshiftAcme1_1RuntimeClasspath", "org.acme:acme-lib")).isEqualTo("1.1.0")
  }

  @Test
  fun `resolves exact versions, pre-releases included`() {
    acme("1.0.0", "1.1.1-RC1")

    assertThat(resolvedVersion("testDownshiftAcme1_0_0RuntimeClasspath", "org.acme:acme-lib")).isEqualTo("1.0.0")
    assertThat(resolvedVersion("testDownshiftAcme1_1_1_RC1RuntimeClasspath", "org.acme:acme-lib"))
        .isEqualTo("1.1.1-RC1")
  }

  @Test
  fun `enforced platform overrides versions declared directly`() {
    project.dependencies.add("testImplementation", "org.acme:acme-lib:1.2.0")
    acme("1.0.+")

    assertThat(resolvedVersion("testDownshiftAcme1_0RuntimeClasspath", "org.acme:acme-lib")).isEqualTo("1.0.1")
  }

  @Test
  fun `not enforced platform lets versions declared directly win`() {
    project.dependencies.add("testImplementation", "org.acme:acme-lib:1.2.0")
    acme("1.0.+", enforced = false)

    assertThat(resolvedVersion("testDownshiftAcme1_0RuntimeClasspath", "org.acme:acme-lib")).isEqualTo("1.2.0")
  }

  @Test
  fun `resolves every platform of a combination`() {
    acme()
    extension().platform("other") { bom.set("org.other:other-bom") }
    extension().combination("legacy") {
      line("acme", "1.0.+")
      line("other", "2.0.+")
    }

    assertThat(resolvedVersion("testDownshiftLegacyRuntimeClasspath", "org.acme:acme-lib")).isEqualTo("1.0.1")
    assertThat(resolvedVersion("testDownshiftLegacyRuntimeClasspath", "org.other:other-lib")).isEqualTo("2.0.0")
  }

  @Test
  fun `configures test task with compiled test classes and excluded tags`() {
    acme("1.0.+")

    val task = project.tasks.getByName("testDownshiftAcme1_0") as TestTask
    val baseTask = project.tasks.getByName("test") as TestTask

    assertThat(task.testClassesDirs.files).isEqualTo(baseTask.testClassesDirs.files)
    assertThat((task.options as JUnitPlatformOptions).excludeTags).containsExactlyInAnyOrder("acme1.1+", "acme1.2+")
  }

  @Test
  fun `configures combination task with excluded tags of every platform`() {
    acme()
    extension().platform("other") { bom.set("org.other:other-bom") }
    extension().combination("legacy") {
      line("acme", "1.1.+")
      line("other", "2.0.+")
    }

    val task = project.tasks.getByName("testDownshiftLegacy") as TestTask

    assertThat((task.options as JUnitPlatformOptions).excludeTags).containsExactlyInAnyOrder("acme1.2+", "other2.1+")
  }
}
