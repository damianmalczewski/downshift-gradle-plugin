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
import org.gradle.api.Project
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
}
