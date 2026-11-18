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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test as TestTask
import org.gradle.kotlin.dsl.getByType
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DownshiftPluginTest {

  private lateinit var project: Project

  @BeforeEach
  fun beforeEach() {
    project = ProjectBuilder.builder().build()
  }

  private fun extension(): DownshiftExtension = project.extensions.getByType()

  @Test
  fun `registers extension without java plugin and no tasks`() {
    project.plugins.apply("io.github.malczuuu.downshift")

    assertThat(project.extensions.findByName("downshift")).isInstanceOf(DownshiftExtension::class.java)
    assertThat(project.tasks.names).doesNotContain("testDownshift")
  }

  @Test
  fun `registers aggregate task for test source set`() {
    project.plugins.apply("java")
    project.plugins.apply("io.github.malczuuu.downshift")

    assertThat(project.tasks.names).contains("testDownshift")
  }

  @Test
  fun `registers test task per declared line`() {
    project.plugins.apply("java")
    project.plugins.apply("io.github.malczuuu.downshift")

    extension().platform("springBoot") {
      bom.set("org.springframework.boot:spring-boot-dependencies")
      lines("3.5.+", "4.0.+")
    }

    assertThat(project.tasks.withType(TestTask::class.java).names)
        .contains("testDownshiftSpringBoot3_5", "testDownshiftSpringBoot4_0")
    assertThat(project.configurations.names)
        .contains("testDownshiftSpringBoot4_0RuntimeOnly", "testDownshiftSpringBoot4_0RuntimeClasspath")
  }

  @Test
  fun `registers test task per combination`() {
    project.plugins.apply("java")
    project.plugins.apply("io.github.malczuuu.downshift")

    extension().combination("legacyStack") { line("springBoot", "4.0.+") }

    assertThat(project.tasks.withType(TestTask::class.java).names).contains("testDownshiftLegacyStack")
  }

  @Test
  fun `registers tasks when plugin is applied before java`() {
    project.plugins.apply("io.github.malczuuu.downshift")
    extension().platform("springBoot") {
      bom.set("org.springframework.boot:spring-boot-dependencies")
      line("4.0.+")
    }
    project.plugins.apply("java")

    assertThat(project.tasks.names).contains("testDownshiftSpringBoot4_0")
  }

  @Test
  fun `defaults to enforced platform attached to check`() {
    project.plugins.apply("io.github.malczuuu.downshift")

    var enforced: Boolean? = null
    extension().platform("springBoot") { enforced = this.enforced.get() }

    assertThat(enforced).isTrue()
    assertThat(extension().attachToCheck.get()).isTrue()
  }

  @Test
  fun `exposes names of registered test tasks`() {
    project.plugins.apply("java")
    project.plugins.apply("io.github.malczuuu.downshift")

    val testTaskNames = extension().testTaskNames
    extension().platform("springBoot") {
      bom.set("org.springframework.boot:spring-boot-dependencies")
      lines(listOf("3.5.+", "4.0.+"))
    }
    extension().combination("legacyStack") { line("springBoot", "3.5.+") }

    assertThat(testTaskNames.get())
        .containsExactlyInAnyOrder(
            "testDownshiftSpringBoot3_5",
            "testDownshiftSpringBoot4_0",
            "testDownshiftLegacyStack",
        )
  }

  @Test
  fun `exposes no test task names without java plugin`() {
    project.plugins.apply("io.github.malczuuu.downshift")

    extension().platform("springBoot") { line("4.0.+") }

    assertThat(extension().testTaskNames.get()).isEmpty()
  }

  @Test
  fun `fails on platform name ending with a digit`() {
    project.plugins.apply("io.github.malczuuu.downshift")

    assertThatThrownBy { extension().platform("log4j2") {} }
        .isInstanceOf(InvalidUserDataException::class.java)
        .hasMessageContaining("platform name must not end with a digit")
  }

  @Test
  fun `fails on combination of platform name ending with a digit`() {
    project.plugins.apply("io.github.malczuuu.downshift")

    assertThatThrownBy { extension().combination("legacyStack") { line("log4j2", "2.24.+") } }
        .isInstanceOf(InvalidUserDataException::class.java)
        .hasMessageContaining("platform name must not end with a digit")
  }

  /** Runs the `afterEvaluate` hooks, which is where the plugin validates the source sets opted into. */
  private fun evaluate() {
    (project as ProjectInternal).evaluate()
  }

  private fun applyPlugins() {
    project.plugins.apply("java")
    project.plugins.apply("io.github.malczuuu.downshift")
  }

  private fun realize(taskName: String) {
    project.tasks.getByName(taskName)
  }

  @Test
  fun `registers tasks of opted-in source set`() {
    applyPlugins()
    val sourceSet = project.extensions.getByType<JavaPluginExtension>().sourceSets.create("integrationTest")
    project.tasks.register(sourceSet.name, TestTask::class.java)

    extension().sourceSet("integrationTest")
    extension().platform("springBoot") { line("4.0.+") }

    assertThat(project.tasks.names).contains("integrationTestDownshift", "integrationTestDownshiftSpringBoot4_0")
  }

  @Test
  fun `evaluates project with known source sets`() {
    applyPlugins()

    assertThatCode { evaluate() }.doesNotThrowAnyException()
  }

  @Test
  fun `evaluates java-less project without checking source sets`() {
    project.plugins.apply("io.github.malczuuu.downshift")
    extension().sourceSet("nope")

    assertThatCode { evaluate() }.doesNotThrowAnyException()
  }

  @Test
  fun `fails evaluation on unknown source set`() {
    applyPlugins()
    extension().sourceSet("nope")

    assertThatThrownBy { evaluate() }
        .rootCause()
        .isInstanceOf(InvalidUserDataException::class.java)
        .hasMessageContaining("no source set named 'nope'")
        .hasMessageContaining("Available: main, test")
  }

  @Test
  fun `fails configuring task of platform without bom`() {
    applyPlugins()
    extension().platform("springBoot") { line("4.0.+") }

    assertThatThrownBy { realize("testDownshiftSpringBoot4_0") }
        .rootCause()
        .isInstanceOf(InvalidUserDataException::class.java)
        .hasMessageContaining("platform 'springBoot' has no bom")
  }

  @Test
  fun `fails configuring task of platform with bom in invalid notation`() {
    applyPlugins()
    extension().platform("springBoot") {
      bom.set("org.springframework.boot:spring-boot-dependencies:4.1.2")
      line("4.0.+")
    }

    assertThatThrownBy { realize("testDownshiftSpringBoot4_0") }
        .rootCause()
        .isInstanceOf(InvalidUserDataException::class.java)
        .hasMessageContaining("bom of platform 'springBoot' must be in 'group:name' notation")
  }

  @Test
  fun `fails configuring task when current line cannot be detected`() {
    applyPlugins()
    extension().platform("springBoot") {
      bom.set("org.springframework.boot:spring-boot-dependencies")
      line("4.0.+")
    }
    evaluate()

    assertThatThrownBy { realize("testDownshiftSpringBoot4_0") }
        .rootCause()
        .isInstanceOf(InvalidUserDataException::class.java)
        .hasMessageContaining("cannot detect which line of org.springframework.boot:spring-boot-dependencies")
  }

  @Test
  fun `uses configured current line`() {
    applyPlugins()
    extension().platform("springBoot") {
      bom.set("org.springframework.boot:spring-boot-dependencies")
      currentLine.set("4.1")
      line("4.0.+")
    }

    assertThatCode { realize("testDownshiftSpringBoot4_0") }.doesNotThrowAnyException()
  }

  @Test
  fun `fails configuring task of combination with undeclared platform`() {
    applyPlugins()
    extension().combination("legacyStack") { line("springBoot", "4.0.+") }

    assertThatThrownBy { realize("testDownshiftLegacyStack") }
        .rootCause()
        .isInstanceOf(InvalidUserDataException::class.java)
        .hasMessageContaining("combination 'legacyStack' refers to undeclared platform 'springBoot'")
  }

  @Test
  fun `fails configuring task of combination without lines`() {
    applyPlugins()
    extension().combination("legacyStack") {}

    assertThatThrownBy { realize("testDownshiftLegacyStack") }
        .rootCause()
        .isInstanceOf(InvalidUserDataException::class.java)
        .hasMessageContaining("combination 'legacyStack' has no lines")
  }

  private fun dependencyNamesOf(taskName: String): List<String> =
      project.tasks.getByName(taskName).let { task -> task.taskDependencies.getDependencies(task).map { it.name } }

  @Test
  fun `attaches aggregate task with every variant to check`() {
    applyPlugins()
    extension().platform("springBoot") {
      bom.set("org.springframework.boot:spring-boot-dependencies")
      currentLine.set("4.1")
      line("4.0.+")
    }

    assertThat(dependencyNamesOf("check")).contains("testDownshift")
    assertThat(dependencyNamesOf("testDownshift")).containsExactly("testDownshiftSpringBoot4_0")
  }

  @Test
  fun `does not attach aggregate task to check when detached`() {
    applyPlugins()
    extension().attachToCheck.set(false)

    assertThat(dependencyNamesOf("check")).doesNotContain("testDownshift")
  }
}
