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
import io.github.malczuuu.downshift.internal.requireValidName
import javax.inject.Inject
import org.gradle.api.Named
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty

/**
 * A set of lines of several platforms that tests are run against together, declared with `downshift {
 * combination("...") { ... } }`. Gets a single test task, e.g. `testDownshiftLegacyStack` for combination
 * `legacyStack`. Tests tagged for a newer line of any of the combined platforms are skipped.
 */
@DownshiftDslMarker
public abstract class DownshiftCombinationSpec @Inject constructor(private val name: String, objects: ObjectFactory) :
    Named {

  /** Lines keyed by platform name. */
  internal val lines: MapProperty<String, VersionLine> =
      objects.mapProperty(String::class.java, VersionLine::class.java)

  /** @return name of this combination, used in its task name */
  override fun getName(): String = name

  /**
   * Sets the line of the platform named [platform] in this combination. The platform must be declared with `downshift {
   * platform("...") { ... } }`, but does not need to declare [line] itself.
   *
   * @param platform name of a declared platform, e.g. `"springBoot"`
   * @param line line in `major.minor` format, e.g. `"4.0"`
   */
  public fun line(platform: String, line: String) {
    requireValidName("platform", platform)
    lines.put(platform, VersionLine.parse(line))
  }
}
