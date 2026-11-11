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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Dependency

/**
 * A BOM replaced by the latest patch of one of its lines on the runtime classpath of a single test task.
 *
 * @property platformName name of the platform declaring the BOM, also the prefix of its tags
 * @property group group of the BOM module
 * @property module name of the BOM module
 * @property line line the tests run against
 * @property enforced whether the BOM is applied as an enforced platform
 * @property declaredLines every line declared for the platform, used to compute excluded tags
 * @property currentLine line the project is compiled against, if configured explicitly
 */
internal data class Swap(
    val platformName: String,
    val group: String,
    val module: String,
    val line: VersionLine,
    val enforced: Boolean,
    val declaredLines: List<VersionLine>,
    val currentLine: VersionLine?,
) {

  /** Dependency notation of the BOM at the latest patch of [line], e.g. `group:module:4.0.+`. */
  val notation: String
    get() = "$group:$module:${line.dynamicVersion}"

  /**
   * Whether this swap replaces the module identified by [group] and [module].
   *
   * @param group group of the module
   * @param module name of the module
   * @return `true` if it is the BOM of this swap
   */
  fun matches(group: String?, module: String): Boolean = this.group == group && this.module == module

  /**
   * Computes tags of tests needing a line newer than [line], see [excludedTagsOf]. The line the project is compiled
   * against is taken from [currentLine], or detected from the version of the BOM among [declaredDependencies].
   *
   * @param declaredDependencies dependencies declared for the runtime classpath of the tested source set
   * @param sourceSetName name of the tested source set, for the error message
   * @return tags to exclude
   * @throws InvalidUserDataException if the line the project is compiled against cannot be determined
   */
  fun excludedTags(declaredDependencies: Iterable<Dependency>, sourceSetName: String): List<String> {
    val current =
        currentLine
            ?: declaredDependencies
                .filter { matches(it.group, it.name) }
                .firstNotNullOfOrNull { dependency -> dependency.version?.let { VersionLine.ofVersion(it) } }
            ?: throw InvalidUserDataException(
                "downshift: cannot detect which line of $group:$module the '$sourceSetName' source set uses. " +
                    "Declare it with a version, e.g. testImplementation(platform(\"$group:$module:<version>\")), " +
                    "or set downshift { platform(\"$platformName\") { currentLine = \"<major.minor>\" } }.",
            )
    return excludedTagsOf(platformName, line, current, declaredLines)
  }
}
