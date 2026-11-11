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
 * A release line of a dependency, identified by its major and minor version (e.g. `4.0` for Spring Boot `4.0.x`).
 *
 * @property major major version
 * @property minor minor version
 */
internal data class VersionLine(val major: Int, val minor: Int) : Comparable<VersionLine> {

  /** Dynamic version selecting the latest patch of this line, e.g. `4.0.+`. */
  val dynamicVersion: String
    get() = "$major.$minor.+"

  override fun compareTo(other: VersionLine): Int = compareValuesBy(this, other, { it.major }, { it.minor })

  override fun toString(): String = "$major.$minor"

  companion object {

    private val LINE_REGEX = Regex("""(\d+)\.(\d+)""")

    private val VERSION_PREFIX_REGEX = Regex("""^(\d+)\.(\d+)(\..*)?$""")

    /**
     * Parses a line declared in the build script, which must be exactly `major.minor`.
     *
     * @param value line to parse, e.g. `"4.0"`
     * @return parsed line
     * @throws InvalidUserDataException if [value] is not `major.minor`
     */
    fun parse(value: String): VersionLine {
      val match =
          LINE_REGEX.matchEntire(value.trim())
              ?: throw InvalidUserDataException(
                  "downshift: line must be in 'major.minor' format (e.g. \"4.0\"), but was: \"$value\".",
              )
      return VersionLine(match.groupValues[1].toInt(), match.groupValues[2].toInt())
    }

    /**
     * Extracts the line of a full version, e.g. `4.1` of `4.1.2` or `4.1.+`.
     *
     * @param version version to extract the line from
     * @return extracted line, or `null` if [version] does not start with `major.minor`
     */
    fun ofVersion(version: String): VersionLine? =
        VERSION_PREFIX_REGEX.matchEntire(version.trim())?.let {
          VersionLine(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
  }
}
