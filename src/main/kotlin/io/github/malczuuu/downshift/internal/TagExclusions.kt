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
 * Builds the tag marking tests that need [line] or newer of the platform named [platformName], e.g. `springBoot4.1+`.
 *
 * @param platformName name of the platform, as declared in the build script
 * @param line first line providing what the test needs
 * @return JUnit Platform tag
 */
internal fun tagOf(platformName: String, line: VersionLine): String = "$platformName$line+"

/**
 * Computes tags of tests to skip when running against [testedLine]: tags of every line newer than it, up to
 * [currentLine]. For each major version from the tested one to the current one, every minor up to the newest line known
 * in that major is covered. Known lines are [declaredLines] and [currentLine]; minors past the newest known line of an
 * older major cannot be guessed and are not covered.
 *
 * @param platformName name of the platform, used as the tag prefix
 * @param testedLine line the tests run against
 * @param currentLine line the project is compiled against
 * @param declaredLines all lines declared for the platform
 * @return tags to exclude, oldest line first
 */
internal fun excludedTagsOf(
    platformName: String,
    testedLine: VersionLine,
    currentLine: VersionLine,
    declaredLines: Collection<VersionLine>,
): List<String> {
  val newestMinorByMajor =
      (declaredLines + currentLine).groupBy { it.major }.mapValues { (_, lines) -> lines.maxOf { it.minor } }
  val tags = mutableListOf<String>()
  for (major in testedLine.major..currentLine.major) {
    val newestMinor = newestMinorByMajor[major] ?: continue
    val firstMinor = if (major == testedLine.major) testedLine.minor + 1 else 0
    for (minor in firstMinor..newestMinor) {
      tags.add(tagOf(platformName, VersionLine(major, minor)))
    }
  }
  return tags
}

/**
 * Determines the line the project is compiled against for the BOM [group]:[module]: [configuredLine] if set, otherwise
 * the line of the version the BOM is declared with among [declaredDependencies].
 *
 * @param platformName name of the platform, for the error message
 * @param group group of the BOM module
 * @param module name of the BOM module
 * @param configuredLine line set explicitly with `currentLine`, if any
 * @param declaredDependencies dependencies declared for the runtime classpath of the tested source set
 * @param sourceSetName name of the tested source set, for the error message
 * @return line the project is compiled against
 * @throws InvalidUserDataException if it is neither configured nor declared with a version
 */
internal fun currentLineOf(
    platformName: String,
    group: String,
    module: String,
    configuredLine: VersionLine?,
    declaredDependencies: Iterable<Dependency>,
    sourceSetName: String,
): VersionLine =
    configuredLine
        ?: declaredDependencies
            .filter { it.group == group && it.name == module }
            .firstNotNullOfOrNull { dependency -> dependency.version?.let { VersionLine.ofVersion(it) } }
        ?: throw InvalidUserDataException(
            "downshift: cannot detect which line of $group:$module the '$sourceSetName' source set uses. " +
                "Declare it with a version, e.g. testImplementation(platform(\"$group:$module:<version>\")), " +
                "or set downshift { platform(\"$platformName\") { currentLine = \"<major.minor>\" } }.",
        )
