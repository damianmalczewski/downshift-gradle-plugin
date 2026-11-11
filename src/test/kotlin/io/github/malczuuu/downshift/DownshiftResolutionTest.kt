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
import io.github.malczuuu.downshift.internal.Swap
import io.github.malczuuu.downshift.internal.VersionSelector
import io.github.malczuuu.downshift.internal.findOverriddenVersions
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.api.internal.project.ProjectInternal
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

  private lateinit var repository: LocalMavenRepository

  @BeforeEach
  fun beforeEach() {
    repository = LocalMavenRepository(File(tempDir, "repository"))
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

  private fun overriddenVersions(configurationName: String, vararg swaps: Swap): List<String> =
      findOverriddenVersions(
          project.configurations.getByName(configurationName).incoming.resolutionResult.root,
          swaps.toList(),
      )

  private fun swap(module: String, version: String, enforced: Boolean = true): Swap {
    val (group, name) = module.split(':')
    return Swap(group, name, VersionSelector.parse(version), enforced)
  }

  private fun overrideAcmeLib(version: String) {
    project.configurations.configureEach {
      resolutionStrategy.eachDependency {
        if (requested.group == "org.acme" && requested.name == "acme-lib") {
          useVersion(version)
        }
      }
    }
  }

  @Test
  fun `accepts qualified released versions when resolving the latest patch of a line`() {
    listOf("3.0.0-jre", "3.0.1-jre", "3.0.2-rc1-jre").forEach { version ->
      repository.library("org.qual", "qual-lib", version)
      repository.bom("org.qual", "qual-bom", version, mapOf("org.qual:qual-lib" to version))
    }
    project.dependencies.apply {
      add("testImplementation", platform("org.qual:qual-bom:3.0.2-rc1-jre"))
      add("testImplementation", "org.qual:qual-lib")
    }
    extension().platform("qual") {
      bom.set("org.qual:qual-bom")
      line("3.0.+")
    }

    assertThat(resolvedVersion("testDownshiftQual3_0RuntimeClasspath", "org.qual:qual-lib")).isEqualTo("3.0.1-jre")
  }

  @Test
  fun `keeps exclusions declared on the original runtime classpath`() {
    project.configurations.getByName("testRuntimeClasspath").exclude(mapOf("group" to "org.other"))
    acme("1.0.+")

    assertThat(resolvedVersion("testDownshiftAcme1_0RuntimeClasspath", "org.acme:acme-lib")).isEqualTo("1.0.1")
    assertThat(resolvedVersion("testDownshiftAcme1_0RuntimeClasspath", "org.other:other-lib")).isNull()
  }

  @Test
  fun `resolves the latest patch of a line despite strict dependency locking`() {
    project.dependencyLocking {
      lockAllConfigurations()
      lockMode.set(LockMode.STRICT)
    }
    acme("1.0.+")

    assertThat(resolvedVersion("testDownshiftAcme1_0RuntimeClasspath", "org.acme:acme-lib")).isEqualTo("1.0.1")
  }

  @Test
  fun `finds no overridden versions of an enforced platform`() {
    acme("1.0.+")

    assertThat(overriddenVersions("testDownshiftAcme1_0RuntimeClasspath", swap("org.acme:acme-bom", "1.0.+"))).isEmpty()
  }

  @Test
  fun `finds versions of an enforced platform overridden by another rule`() {
    overrideAcmeLib("1.2.0")
    acme("1.0.+")

    assertThat(overriddenVersions("testDownshiftAcme1_0RuntimeClasspath", swap("org.acme:acme-bom", "1.0.+")))
        .containsExactly("org.acme:acme-lib:1.2.0 instead of 1.0.1")
  }

  @Test
  fun `ignores overridden versions of a not enforced platform`() {
    overrideAcmeLib("1.2.0")
    acme("1.0.+", enforced = false)

    assertThat(
            overriddenVersions(
                "testDownshiftAcme1_0RuntimeClasspath",
                swap("org.acme:acme-bom", "1.0.+", enforced = false),
            ),
        )
        .isEmpty()
  }

  @Test
  fun `finds versions overridden in boms imported by an enforced platform`() {
    stackImportingAcme()
    overrideAcmeLib("1.2.0")
    extension().platform("stack") {
      bom.set("org.stack:stack-bom")
      line("1.0.+")
    }

    assertThat(overriddenVersions("testDownshiftStack1_0RuntimeClasspath", swap("org.stack:stack-bom", "1.0.+")))
        .containsExactly("org.acme:acme-lib:1.2.0 instead of 1.0.1")
  }

  private fun stackImportingAcme() {
    repository.bom("org.stack", "stack-bom", "1.0.0", emptyMap(), mapOf("org.acme:acme-bom" to "1.0.1"))
    repository.bom("org.stack", "stack-bom", "1.1.0", emptyMap(), mapOf("org.acme:acme-bom" to "1.2.0"))
    project.dependencies.apply { add("testImplementation", platform("org.stack:stack-bom:1.1.0")) }
  }

  @Test
  fun `finds versions managed by two enforced platforms of a combination`() {
    stackImportingAcme()
    acme()
    extension().platform("stack") { bom.set("org.stack:stack-bom") }
    extension().combination("legacy") {
      line("stack", "1.0.+")
      line("acme", "1.1.+")
    }

    assertThat(
            overriddenVersions(
                "testDownshiftLegacyRuntimeClasspath",
                swap("org.stack:stack-bom", "1.0.+"),
                swap("org.acme:acme-bom", "1.1.+"),
            ),
        )
        .singleElement()
        .asString()
        .startsWith("org.acme:acme-lib:")
  }

  @Test
  fun `accepts versions of a not enforced platform overridden in a combination`() {
    stackImportingAcme()
    acme(enforced = false)
    extension().platform("stack") { bom.set("org.stack:stack-bom") }
    extension().combination("legacy") {
      line("stack", "1.0.+")
      line("acme", "1.1.+")
    }

    assertThat(resolvedVersion("testDownshiftLegacyRuntimeClasspath", "org.acme:acme-lib")).isEqualTo("1.0.1")
    assertThat(
            overriddenVersions(
                "testDownshiftLegacyRuntimeClasspath",
                swap("org.stack:stack-bom", "1.0.+"),
                swap("org.acme:acme-bom", "1.1.+", enforced = false),
            ),
        )
        .isEmpty()
  }

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
    (project.tasks.getByName("test") as TestTask).useJUnitPlatform()
    (project as ProjectInternal).evaluate()

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
    (project.tasks.getByName("test") as TestTask).useJUnitPlatform()
    (project as ProjectInternal).evaluate()

    val task = project.tasks.getByName("testDownshiftLegacy") as TestTask

    assertThat((task.options as JUnitPlatformOptions).excludeTags).containsExactlyInAnyOrder("acme1.2+", "other2.1+")
  }
}
