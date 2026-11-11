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

/**
 * Version of a BOM that tests are run against, declared with `line(...)`: either the latest patch of a line (`4.0.+`)
 * or an exact version (`4.0.8`).
 *
 * @property notation version as declared, used verbatim in the dependency notation of the BOM
 * @property line line the version belongs to, used to compute excluded tags
 * @property dynamic whether it selects the latest patch of [line] rather than an exact version
 */
internal data class VersionSelector(val notation: String, val line: VersionLine, val dynamic: Boolean) {

  /** Segment of task and configuration names, e.g. `4_0` for `4.0.+` and `4_0_8` for `4.0.8`. */
  val taskNameSegment: String
    get() = if (dynamic) "${line.major}_${line.minor}" else notation.replace(NON_ALPHANUMERIC_REGEX, "_")

  override fun toString(): String = notation

  companion object {

    private val DYNAMIC_REGEX = Regex("""(\d+)\.(\d+)\.\+""")

    private val EXACT_REGEX = Regex("""(\d+)\.(\d+)\.\d+([.\-][0-9A-Za-z][0-9A-Za-z.\-]*)?""")

    private val NON_ALPHANUMERIC_REGEX = Regex("[^0-9A-Za-z]+")

    /**
     * Parses a version declared in the build script.
     *
     * @param value `major.minor.+` for the latest patch of a line (e.g. `"4.0.+"`), or an exact version with at least
     *   three segments (e.g. `"4.0.8"`)
     * @return parsed version
     * @throws InvalidUserDataException if [value] is neither
     */
    fun parse(value: String): VersionSelector {
      val trimmed = value.trim()
      DYNAMIC_REGEX.matchEntire(trimmed)?.let {
        return VersionSelector(trimmed, VersionLine(it.groupValues[1].toInt(), it.groupValues[2].toInt()), true)
      }
      EXACT_REGEX.matchEntire(trimmed)?.let {
        return VersionSelector(trimmed, VersionLine(it.groupValues[1].toInt(), it.groupValues[2].toInt()), false)
      }
      throw InvalidUserDataException(
          "downshift: line must be either 'major.minor.+' for the latest patch of a line (e.g. \"4.0.+\") or an " +
              "exact version (e.g. \"4.0.8\"), but was: \"$value\".",
      )
    }
  }
}
