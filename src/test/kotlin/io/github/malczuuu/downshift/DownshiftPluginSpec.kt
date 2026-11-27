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
import io.github.malczuuu.downshift.fixtures.rootCause
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test as TestTask
import org.gradle.kotlin.dsl.getByType
import org.gradle.testfixtures.ProjectBuilder

class DownshiftPluginSpec :
    FunSpec(
        {
          lateinit var project: Project

          beforeEach { project = ProjectBuilder.builder().build() }

          fun extension(): DownshiftExtension = project.extensions.getByType()

          /** Runs the `afterEvaluate` hooks, which is where the plugin validates the source sets opted into. */
          fun evaluate() {
            (project as ProjectInternal).evaluate()
          }

          fun applyPlugins() {
            project.plugins.apply("java")
            project.plugins.apply("io.github.malczuuu.downshift")
          }

          fun realize(taskName: String) {
            project.tasks.getByName(taskName)
          }

          fun dependencyNamesOf(taskName: String): List<String> =
              project.tasks.getByName(taskName).let { task ->
                task.taskDependencies.getDependencies(task).map { it.name }
              }

          test("registers extension without java plugin and no tasks") {
            project.plugins.apply("io.github.malczuuu.downshift")

            project.extensions.findByName("downshift").shouldBeInstanceOf<DownshiftExtension>()
            project.tasks.names shouldNotContain "testDownshift"
          }

          test("registers aggregate task for test source set") {
            applyPlugins()

            project.tasks.names shouldContain "testDownshift"
          }

          test("registers test task per declared line") {
            applyPlugins()

            extension().platform("springBoot") {
              bom.set("org.springframework.boot:spring-boot-dependencies")
              lines("3.5.+", "4.0.+")
            }

            project.tasks.withType(TestTask::class.java).names shouldContainAll
                listOf("testDownshiftSpringBoot3_5", "testDownshiftSpringBoot4_0")
            project.configurations.names shouldContainAll
                listOf("testDownshiftSpringBoot4_0RuntimeOnly", "testDownshiftSpringBoot4_0RuntimeClasspath")
          }

          test("registers test task per combination") {
            applyPlugins()

            extension().combination("legacyStack") { line("springBoot", "4.0.+") }

            project.tasks.withType(TestTask::class.java).names shouldContain "testDownshiftLegacyStack"
          }

          test("registers tasks when plugin is applied before java") {
            project.plugins.apply("io.github.malczuuu.downshift")
            extension().platform("springBoot") {
              bom.set("org.springframework.boot:spring-boot-dependencies")
              line("4.0.+")
            }
            project.plugins.apply("java")

            project.tasks.names shouldContain "testDownshiftSpringBoot4_0"
          }

          test("defaults to enforced platform attached to check") {
            project.plugins.apply("io.github.malczuuu.downshift")

            var enforced: Boolean? = null
            extension().platform("springBoot") { enforced = this.enforced.get() }

            enforced shouldBe true
            extension().attachToCheck.get() shouldBe true
          }

          test("exposes names of registered test tasks") {
            applyPlugins()

            val testTaskNames = extension().testTaskNames
            extension().platform("springBoot") {
              bom.set("org.springframework.boot:spring-boot-dependencies")
              lines(listOf("3.5.+", "4.0.+"))
            }
            extension().combination("legacyStack") { line("springBoot", "3.5.+") }

            testTaskNames.get() shouldContainExactlyInAnyOrder
                listOf("testDownshiftSpringBoot3_5", "testDownshiftSpringBoot4_0", "testDownshiftLegacyStack")
          }

          test("exposes no test task names without java plugin") {
            project.plugins.apply("io.github.malczuuu.downshift")

            extension().platform("springBoot") { line("4.0.+") }

            extension().testTaskNames.get().shouldBeEmpty()
          }

          test("fails on platform name ending with a digit") {
            project.plugins.apply("io.github.malczuuu.downshift")

            shouldThrow<InvalidUserDataException> { extension().platform("log4j2") {} }.message shouldContain
                "platform name must not end with a digit"
          }

          test("fails on combination of platform name ending with a digit") {
            project.plugins.apply("io.github.malczuuu.downshift")

            shouldThrow<InvalidUserDataException> {
                  extension().combination("legacyStack") { line("log4j2", "2.24.+") }
                }
                .message shouldContain "platform name must not end with a digit"
          }

          test("registers tasks of opted-in source set") {
            applyPlugins()
            val sourceSet = project.extensions.getByType<JavaPluginExtension>().sourceSets.create("integrationTest")
            project.tasks.register(sourceSet.name, TestTask::class.java)

            extension().sourceSet("integrationTest")
            extension().platform("springBoot") { line("4.0.+") }

            project.tasks.names shouldContainAll
                listOf("integrationTestDownshift", "integrationTestDownshiftSpringBoot4_0")
          }

          test("evaluates project with known source sets") {
            applyPlugins()

            shouldNotThrowAny { evaluate() }
          }

          test("evaluates java-less project without checking source sets") {
            project.plugins.apply("io.github.malczuuu.downshift")
            extension().sourceSet("nope")

            shouldNotThrowAny { evaluate() }
          }

          test("fails evaluation on unknown source set") {
            applyPlugins()
            extension().sourceSet("nope")

            val cause = shouldThrow<Throwable> { evaluate() }.rootCause

            cause.shouldBeInstanceOf<InvalidUserDataException>()
            cause.message shouldContain "no source set named 'nope'"
            cause.message shouldContain "Available: main, test"
          }

          test("fails configuring task of platform without bom") {
            applyPlugins()
            extension().platform("springBoot") { line("4.0.+") }

            val cause = shouldThrow<Throwable> { realize("testDownshiftSpringBoot4_0") }.rootCause

            cause.shouldBeInstanceOf<InvalidUserDataException>()
            cause.message shouldContain "platform 'springBoot' has no bom"
          }

          test("fails configuring task of platform with bom in invalid notation") {
            applyPlugins()
            extension().platform("springBoot") {
              bom.set("org.springframework.boot:spring-boot-dependencies:4.1.2")
              line("4.0.+")
            }

            val cause = shouldThrow<Throwable> { realize("testDownshiftSpringBoot4_0") }.rootCause

            cause.shouldBeInstanceOf<InvalidUserDataException>()
            cause.message shouldContain "bom of platform 'springBoot' must be in 'group:name' notation"
          }

          test("fails configuring task when current line cannot be detected") {
            applyPlugins()
            extension().platform("springBoot") {
              bom.set("org.springframework.boot:spring-boot-dependencies")
              line("4.0.+")
            }
            evaluate()

            val cause = shouldThrow<Throwable> { realize("testDownshiftSpringBoot4_0") }.rootCause

            cause.shouldBeInstanceOf<InvalidUserDataException>()
            cause.message shouldContain "cannot detect which line of org.springframework.boot:spring-boot-dependencies"
          }

          test("uses configured current line") {
            applyPlugins()
            extension().platform("springBoot") {
              bom.set("org.springframework.boot:spring-boot-dependencies")
              currentLine.set("4.1")
              line("4.0.+")
            }

            shouldNotThrowAny { realize("testDownshiftSpringBoot4_0") }
          }

          test("fails configuring task of combination with undeclared platform") {
            applyPlugins()
            extension().combination("legacyStack") { line("springBoot", "4.0.+") }

            val cause = shouldThrow<Throwable> { realize("testDownshiftLegacyStack") }.rootCause

            cause.shouldBeInstanceOf<InvalidUserDataException>()
            cause.message shouldContain "combination 'legacyStack' refers to undeclared platform 'springBoot'"
          }

          test("fails configuring task of combination without lines") {
            applyPlugins()
            extension().combination("legacyStack") {}

            val cause = shouldThrow<Throwable> { realize("testDownshiftLegacyStack") }.rootCause

            cause.shouldBeInstanceOf<InvalidUserDataException>()
            cause.message shouldContain "combination 'legacyStack' has no lines"
          }

          test("attaches aggregate task with every variant to check") {
            applyPlugins()
            extension().platform("springBoot") {
              bom.set("org.springframework.boot:spring-boot-dependencies")
              currentLine.set("4.1")
              line("4.0.+")
            }

            dependencyNamesOf("check") shouldContain "testDownshift"
            dependencyNamesOf("testDownshift") shouldContainExactly listOf("testDownshiftSpringBoot4_0")
          }

          test("does not attach aggregate task to check when detached") {
            applyPlugins()
            extension().attachToCheck.set(false)

            dependencyNamesOf("check") shouldNotContain "testDownshift"
          }
        },
    )
