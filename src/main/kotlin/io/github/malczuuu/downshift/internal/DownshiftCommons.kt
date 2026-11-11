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

import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.util.GradleVersion

/** Oldest Gradle release the plugin supports, enforced when it is applied. */
internal const val MINIMUM_GRADLE_VERSION = "8.4"

/** Name of the source set whose tests are run against older lines by default. */
internal const val DEFAULT_SOURCE_SET_NAME = "test"

/** Names of platforms and combinations, used verbatim in tags and capitalized in task names. */
private val NAME_REGEX = Regex("[a-zA-Z][a-zA-Z0-9]*")

/** Released versions only: numeric segments, optionally followed by a `.RELEASE` or `.Final` qualifier. */
private val STABLE_VERSION_REGEX = Regex("""\d+(\.\d+)*(\.RELEASE|\.Final)?""")

/**
 * Fails on a Gradle older than [MINIMUM_GRADLE_VERSION], where the plugin would break on missing APIs with a far less
 * obvious error.
 *
 * @param current Gradle version the plugin is applied on
 * @throws GradleException if [current] is older than [MINIMUM_GRADLE_VERSION]
 */
internal fun requireMinimumGradleVersion(current: GradleVersion) {
  if (current < GradleVersion.version(MINIMUM_GRADLE_VERSION)) {
    throw GradleException(
        "The io.github.malczuuu.downshift plugin requires Gradle $MINIMUM_GRADLE_VERSION or later, but was applied " +
            "to Gradle ${current.version}.",
    )
  }
}

/**
 * Fails on a platform or combination name that would not form valid tag and task names.
 *
 * @param kind what is named, for the error message
 * @param name name to validate
 * @throws InvalidUserDataException if [name] is not alphanumeric starting with a letter
 */
internal fun requireValidName(kind: String, name: String) {
  if (!NAME_REGEX.matches(name)) {
    throw InvalidUserDataException(
        "downshift: $kind name must be alphanumeric and start with a letter (e.g. \"springBoot\"), but was: \"$name\".",
    )
  }
}

/**
 * Whether [version] is a released one, so that the latest patch of a line never resolves to a milestone, release
 * candidate or snapshot.
 *
 * @param version candidate version
 * @return `true` for a released version
 */
internal fun isStableVersion(version: String): Boolean = STABLE_VERSION_REGEX.matches(version)

/**
 * Upper-cases the first character, turning a DSL name into a task name segment (`springBoot` -> `SpringBoot`).
 *
 * @return capitalized name
 * @receiver name to capitalize
 */
internal fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }
