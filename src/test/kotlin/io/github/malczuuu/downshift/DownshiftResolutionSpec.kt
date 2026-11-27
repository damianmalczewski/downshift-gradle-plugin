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
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import java.io.File
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.testing.Test as TestTask
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.kotlin.dsl.getByType
import org.gradle.testfixtures.ProjectBuilder

/**
 * Resolves runtime classpaths registered by the plugin against a local Maven repository, offline. Complements the
 * TestKit functional tests by covering resolution rules in-process, where they are measured by coverage.
 */
class DownshiftResolutionSpec :
    FunSpec(
        {
          lateinit var project: Project
          lateinit var repository: LocalMavenRepository

          beforeEach {
            val tempDir = tempdir()
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

          fun extension(): DownshiftExtension = project.extensions.getByType()

          fun acme(vararg lines: String, enforced: Boolean = true) {
            extension().platform("acme") {
              bom.set("org.acme:acme-bom")
              this.enforced.set(enforced)
              lines(*lines)
            }
          }

          fun resolvedVersion(configurationName: String, module: String): String? =
              project.configurations
                  .getByName(configurationName)
                  .incoming
                  .resolutionResult
                  .allComponents
                  .map { it.id }
                  .filterIsInstance<ModuleComponentIdentifier>()
                  .firstOrNull { "${it.group}:${it.module}" == module }
                  ?.version

          fun overriddenVersions(configurationName: String, vararg swaps: Swap): List<String> =
              findOverriddenVersions(
                  project.configurations.getByName(configurationName).incoming.resolutionResult.root,
                  swaps.toList(),
              )

          fun swap(module: String, version: String, enforced: Boolean = true): Swap {
            val (group, name) = module.split(':')
            return Swap(group, name, VersionSelector.parse(version), enforced)
          }

          fun overrideAcmeLib(version: String) {
            project.configurations.configureEach {
              resolutionStrategy.eachDependency {
                if (requested.group == "org.acme" && requested.name == "acme-lib") {
                  useVersion(version)
                }
              }
            }
          }

          fun stackImportingAcme() {
            repository.bom("org.stack", "stack-bom", "1.0.0", emptyMap(), mapOf("org.acme:acme-bom" to "1.0.1"))
            repository.bom("org.stack", "stack-bom", "1.1.0", emptyMap(), mapOf("org.acme:acme-bom" to "1.2.0"))
            project.dependencies.apply { add("testImplementation", platform("org.stack:stack-bom:1.1.0")) }
          }

          test("accepts qualified released versions when resolving the latest patch of a line") {
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

            resolvedVersion("testDownshiftQual3_0RuntimeClasspath", "org.qual:qual-lib") shouldBe "3.0.1-jre"
          }

          test("keeps exclusions declared on the original runtime classpath") {
            project.configurations.getByName("testRuntimeClasspath").exclude(mapOf("group" to "org.other"))
            acme("1.0.+")

            resolvedVersion("testDownshiftAcme1_0RuntimeClasspath", "org.acme:acme-lib") shouldBe "1.0.1"
            resolvedVersion("testDownshiftAcme1_0RuntimeClasspath", "org.other:other-lib") shouldBe null
          }

          test("resolves the latest patch of a line despite strict dependency locking") {
            project.dependencyLocking {
              lockAllConfigurations()
              lockMode.set(LockMode.STRICT)
            }
            acme("1.0.+")

            resolvedVersion("testDownshiftAcme1_0RuntimeClasspath", "org.acme:acme-lib") shouldBe "1.0.1"
          }

          test("finds no overridden versions of an enforced platform") {
            acme("1.0.+")

            overriddenVersions("testDownshiftAcme1_0RuntimeClasspath", swap("org.acme:acme-bom", "1.0.+"))
                .shouldBeEmpty()
          }

          test("finds versions of an enforced platform overridden by another rule") {
            overrideAcmeLib("1.2.0")
            acme("1.0.+")

            overriddenVersions(
                "testDownshiftAcme1_0RuntimeClasspath",
                swap("org.acme:acme-bom", "1.0.+"),
            ) shouldContainExactly listOf("org.acme:acme-lib:1.2.0 instead of 1.0.1")
          }

          test("ignores overridden versions of a not enforced platform") {
            overrideAcmeLib("1.2.0")
            acme("1.0.+", enforced = false)

            overriddenVersions(
                    "testDownshiftAcme1_0RuntimeClasspath",
                    swap("org.acme:acme-bom", "1.0.+", enforced = false),
                )
                .shouldBeEmpty()
          }

          test("finds versions overridden in boms imported by an enforced platform") {
            stackImportingAcme()
            overrideAcmeLib("1.2.0")
            extension().platform("stack") {
              bom.set("org.stack:stack-bom")
              line("1.0.+")
            }

            overriddenVersions(
                "testDownshiftStack1_0RuntimeClasspath",
                swap("org.stack:stack-bom", "1.0.+"),
            ) shouldContainExactly listOf("org.acme:acme-lib:1.2.0 instead of 1.0.1")
          }

          test("finds versions managed by two enforced platforms of a combination") {
            stackImportingAcme()
            acme()
            extension().platform("stack") { bom.set("org.stack:stack-bom") }
            extension().combination("legacy") {
              line("stack", "1.0.+")
              line("acme", "1.1.+")
            }

            val overridden =
                overriddenVersions(
                    "testDownshiftLegacyRuntimeClasspath",
                    swap("org.stack:stack-bom", "1.0.+"),
                    swap("org.acme:acme-bom", "1.1.+"),
                )

            overridden.size shouldBe 1
            overridden.single() shouldStartWith "org.acme:acme-lib:"
          }

          test("accepts versions of a not enforced platform overridden in a combination") {
            stackImportingAcme()
            acme(enforced = false)
            extension().platform("stack") { bom.set("org.stack:stack-bom") }
            extension().combination("legacy") {
              line("stack", "1.0.+")
              line("acme", "1.1.+")
            }

            resolvedVersion("testDownshiftLegacyRuntimeClasspath", "org.acme:acme-lib") shouldBe "1.0.1"
            overriddenVersions(
                    "testDownshiftLegacyRuntimeClasspath",
                    swap("org.stack:stack-bom", "1.0.+"),
                    swap("org.acme:acme-bom", "1.1.+", enforced = false),
                )
                .shouldBeEmpty()
          }

          test("keeps declared versions on the original runtime classpath") {
            acme("1.0.+")

            resolvedVersion("testRuntimeClasspath", "org.acme:acme-lib") shouldBe "1.2.0"
          }

          test("resolves the latest patch of a line") {
            acme("1.0.+")

            resolvedVersion("testDownshiftAcme1_0RuntimeClasspath", "org.acme:acme-bom") shouldBe "1.0.1"
            resolvedVersion("testDownshiftAcme1_0RuntimeClasspath", "org.acme:acme-lib") shouldBe "1.0.1"
            resolvedVersion("testDownshiftAcme1_0RuntimeClasspath", "org.other:other-lib") shouldBe "2.1.0"
          }

          test("skips pre-releases when resolving the latest patch of a line") {
            acme("1.1.+")

            resolvedVersion("testDownshiftAcme1_1RuntimeClasspath", "org.acme:acme-lib") shouldBe "1.1.0"
          }

          test("resolves exact versions, pre-releases included") {
            acme("1.0.0", "1.1.1-RC1")

            resolvedVersion("testDownshiftAcme1_0_0RuntimeClasspath", "org.acme:acme-lib") shouldBe "1.0.0"
            resolvedVersion("testDownshiftAcme1_1_1_RC1RuntimeClasspath", "org.acme:acme-lib") shouldBe "1.1.1-RC1"
          }

          test("enforced platform overrides versions declared directly") {
            project.dependencies.add("testImplementation", "org.acme:acme-lib:1.2.0")
            acme("1.0.+")

            resolvedVersion("testDownshiftAcme1_0RuntimeClasspath", "org.acme:acme-lib") shouldBe "1.0.1"
          }

          test("not enforced platform lets versions declared directly win") {
            project.dependencies.add("testImplementation", "org.acme:acme-lib:1.2.0")
            acme("1.0.+", enforced = false)

            resolvedVersion("testDownshiftAcme1_0RuntimeClasspath", "org.acme:acme-lib") shouldBe "1.2.0"
          }

          test("resolves every platform of a combination") {
            acme()
            extension().platform("other") { bom.set("org.other:other-bom") }
            extension().combination("legacy") {
              line("acme", "1.0.+")
              line("other", "2.0.+")
            }

            resolvedVersion("testDownshiftLegacyRuntimeClasspath", "org.acme:acme-lib") shouldBe "1.0.1"
            resolvedVersion("testDownshiftLegacyRuntimeClasspath", "org.other:other-lib") shouldBe "2.0.0"
          }

          test("configures test task with compiled test classes and excluded tags") {
            acme("1.0.+")
            (project.tasks.getByName("test") as TestTask).useJUnitPlatform()
            (project as ProjectInternal).evaluate()

            val task = project.tasks.getByName("testDownshiftAcme1_0") as TestTask
            val baseTask = project.tasks.getByName("test") as TestTask

            task.testClassesDirs.files shouldBe baseTask.testClassesDirs.files
            (task.options as JUnitPlatformOptions).excludeTags shouldContainExactlyInAnyOrder
                setOf("acme1.1+", "acme1.2+")
          }

          test("configures combination task with excluded tags of every platform") {
            acme()
            extension().platform("other") { bom.set("org.other:other-bom") }
            extension().combination("legacy") {
              line("acme", "1.1.+")
              line("other", "2.0.+")
            }
            (project.tasks.getByName("test") as TestTask).useJUnitPlatform()
            (project as ProjectInternal).evaluate()

            val task = project.tasks.getByName("testDownshiftLegacy") as TestTask

            (task.options as JUnitPlatformOptions).excludeTags shouldContainExactlyInAnyOrder
                setOf("acme1.2+", "other2.1+")
          }
        },
    )
