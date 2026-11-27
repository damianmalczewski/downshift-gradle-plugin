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

/** Splits a version into tokens on separators and on every boundary between digits and letters. */
private val VERSION_TOKEN_REGEX = Regex("""[0-9]+|[a-zA-Z]+""")

/**
 * Tokens marking a version as not released (`4.0.0-M1`, `2.19.0-rc1`, `7.0.0.Beta5`, `2.2.0-dev-123`), compared
 * case-insensitively. Any other qualifier is part of a released version, e.g. `33.4.8-jre` or `4.1.115.Final`.
 */
private val PRE_RELEASE_TOKENS =
    setOf(
        "a",
        "alpha",
        "b",
        "beta",
        "cr",
        "dev",
        "ea",
        "eap",
        "incubating",
        "m",
        "milestone",
        "nightly",
        "pre",
        "preview",
        "rc",
        "snapshot",
    )

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
 * Fails on a platform name that would not form valid tag and task names. On top of [requireValidName], it must not end
 * with a digit, which would run into the line in its tags (`log4j2` and `2.25` would make `log4j22.25+`).
 *
 * @param name name to validate
 * @throws InvalidUserDataException if [name] is not alphanumeric starting with a letter, or ends with a digit
 */
internal fun requireValidPlatformName(name: String) {
  requireValidName("platform", name)
  if (name.last().isDigit()) {
    throw InvalidUserDataException(
        "downshift: platform name must not end with a digit, as it is followed by the line in tags " +
            "(\"${name}2.0+\"), but was: \"$name\". Drop the digit, e.g. \"${name.trimEnd { it.isDigit() }}\".",
    )
  }
}

/**
 * Whether [version] is a released one, so that the latest patch of a line never resolves to a milestone, release
 * candidate or snapshot. A version is released unless one of its tokens marks a pre-release, so qualifiers of released
 * versions (`-jre`, `-android`, `.RELEASE`, `.Final`) are accepted.
 *
 * @param version candidate version
 * @return `true` for a released version
 */
internal fun isStableVersion(version: String): Boolean =
    VERSION_TOKEN_REGEX.findAll(version).none { it.value.lowercase() in PRE_RELEASE_TOKENS }

/**
 * Upper-cases the first character, turning a DSL name into a task name segment (`springBoot` -> `SpringBoot`).
 *
 * @return capitalized name
 * @receiver name to capitalize
 */
internal fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }
