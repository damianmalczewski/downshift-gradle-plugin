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

package io.github.malczuuu.downshift.dsl

import io.github.malczuuu.downshift.internal.VersionLine
import javax.inject.Inject
import org.gradle.api.DomainObjectSet
import org.gradle.api.Named
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

/**
 * A BOM whose older lines tests are run against, declared with `downshift { platform("...") { ... } }`. Each declared
 * line gets its own test task, e.g. `testDownshiftSpringBoot4_0` for line `4.0` of platform `springBoot`.
 *
 * The platform name is also the prefix of tags marking tests that need a newer line, e.g. `@Tag("springBoot4.1+")` for
 * a test relying on API added in `4.1`. Such tests are skipped when running against older lines.
 */
@DownshiftDslMarker
public abstract class DownshiftPlatformSpec @Inject constructor(private val name: String, objects: ObjectFactory) :
    Named {

  /**
   * Module of the BOM in `group:name` notation, without a version, e.g.
   * `"org.springframework.boot:spring-boot-dependencies"`. Required.
   */
  public abstract val bom: Property<String>

  /**
   * Whether the BOM is applied as an enforced platform, overriding versions requested by any other dependency (like
   * `enforcedPlatform(...)`), instead of only recommending them (like `platform(...)`). Defaults to `true`.
   *
   * Two enforced BOMs managing the same module with different versions cannot be resolved together, so set it to
   * `false` for one of them when combining such platforms.
   */
  public abstract val enforced: Property<Boolean>

  /**
   * Line the project is compiled against, in `major.minor` format. Only needed to compute which tags to exclude when it
   * cannot be detected, i.e. when the BOM is not declared as a dependency of the tested source set with a version.
   */
  public abstract val currentLine: Property<String>

  /** Lines to run tests against, in declaration order. */
  internal val lines: DomainObjectSet<VersionLine> = objects.domainObjectSet(VersionLine::class.java)

  /** @return name of this platform, used in task names and as the prefix of tags */
  override fun getName(): String = name

  /**
   * Adds a line to run tests against, in `major.minor` format. The latest released patch of the line is used, e.g.
   * `4.0.9` for `"4.0"`.
   *
   * @param line line in `major.minor` format, e.g. `"4.0"`
   */
  public fun line(line: String) {
    lines.add(VersionLine.parse(line))
  }

  /**
   * Adds lines to run tests against, see [line].
   *
   * @param lines lines in `major.minor` format, e.g. `"4.0"`
   */
  public fun lines(vararg lines: String) {
    lines.forEach { line(it) }
  }

  /**
   * Adds lines to run tests against, see [line].
   *
   * @param lines lines in `major.minor` format, e.g. `"4.0"`
   */
  public fun lines(lines: Iterable<String>) {
    lines.forEach { line(it) }
  }
}
