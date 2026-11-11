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

package io.github.malczuuu.downshift.internal

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Dependency
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class TagExclusionsTest {

  @Test
  fun `builds tag of a line`() {
    assertThat(tagOf("springBoot", VersionLine(4, 1))).isEqualTo("springBoot4.1+")
  }

  @Test
  fun `excludes every newer minor up to the current line`() {
    val tags = excludedTagsOf("springBoot", VersionLine(4, 0), VersionLine(4, 3), listOf(VersionLine(4, 0)))

    assertThat(tags).containsExactly("springBoot4.1+", "springBoot4.2+", "springBoot4.3+")
  }

  @Test
  fun `excludes nothing for the current line`() {
    val tags = excludedTagsOf("springBoot", VersionLine(4, 1), VersionLine(4, 1), listOf(VersionLine(4, 1)))

    assertThat(tags).isEmpty()
  }

  @Test
  fun `excludes nothing for a line newer than the current one`() {
    val tags = excludedTagsOf("springBoot", VersionLine(4, 2), VersionLine(4, 1), listOf(VersionLine(4, 2)))

    assertThat(tags).isEmpty()
  }

  @Test
  fun `excludes minors of newer majors from zero`() {
    val tags =
        excludedTagsOf(
            "springBoot",
            VersionLine(3, 4),
            VersionLine(4, 1),
            listOf(VersionLine(3, 4), VersionLine(3, 5), VersionLine(4, 0)),
        )

    assertThat(tags).containsExactly("springBoot3.5+", "springBoot4.0+", "springBoot4.1+")
  }

  @Test
  fun `skips majors without known lines`() {
    val tags = excludedTagsOf("kotlin", VersionLine(1, 9), VersionLine(3, 0), listOf(VersionLine(1, 9)))

    assertThat(tags).containsExactly("kotlin3.0+")
  }

  private val dependencies = ProjectBuilder.builder().build().dependencies

  private fun declared(vararg notations: String): List<Dependency> = notations.map { dependencies.create(it) }

  @Test
  fun `prefers configured current line`() {
    val line =
        currentLineOf(
            "springBoot",
            "org.acme",
            "acme-bom",
            VersionLine(4, 1),
            declared("org.acme:acme-bom:3.5.0"),
            "test",
        )

    assertThat(line).isEqualTo(VersionLine(4, 1))
  }

  @Test
  fun `detects current line from declared bom version`() {
    val line =
        currentLineOf(
            "springBoot",
            "org.acme",
            "acme-bom",
            null,
            declared("org.acme:other:9.9.9", "org.acme:acme-bom:4.1.2"),
            "test",
        )

    assertThat(line).isEqualTo(VersionLine(4, 1))
  }

  @Test
  fun `fails when current line is neither configured nor declared`() {
    assertThatThrownBy {
          currentLineOf("springBoot", "org.acme", "acme-bom", null, declared("org.acme:acme-bom"), "integrationTest")
        }
        .isInstanceOf(InvalidUserDataException::class.java)
        .hasMessageContaining("cannot detect which line of org.acme:acme-bom the 'integrationTest' source set uses")
        .hasMessageContaining("platform(\"springBoot\") { currentLine")
  }
}
