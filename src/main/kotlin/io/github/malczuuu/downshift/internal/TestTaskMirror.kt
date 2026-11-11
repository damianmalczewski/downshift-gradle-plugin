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

import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.junit.JUnitOptions
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.api.tasks.testing.testng.TestNGOptions

/**
 * Copies settings of [base] to [variant], so that the same tests run the same way, only on another classpath. Settings
 * the variant already has (e.g. set by `tasks.withType<Test>().configureEach { }` on both tasks) are kept, not
 * duplicated. JVM argument providers are not copied, as plugins attach their own ones (e.g. coverage agents) to every
 * `Test` task they want them on.
 *
 * The test framework of [base] is used, skipping tests tagged with [excludedTags]: as JUnit Platform tags, as TestNG
 * groups, or not at all for JUnit 4, which has no tags.
 *
 * @param base test task of the source set
 * @param variant test task running the same tests on another classpath
 * @param excludedTags tags of tests to skip on [variant]
 * @return `false` if [excludedTags] could not be applied, as [base] runs on JUnit 4
 */
internal fun mirrorTestTask(base: Test, variant: Test, excludedTags: List<String>): Boolean {
  variant.jvmArgs = mergeArguments(base.jvmArgs.orEmpty(), variant.jvmArgs.orEmpty())
  base.systemProperties.forEach { (key, value) -> variant.systemProperties.putIfAbsent(key, value) }
  base.environment.forEach { (key, value) -> variant.environment.putIfAbsent(key, value) }
  base.includes.filterNot { it in variant.includes }.forEach { variant.include(it) }
  base.excludes.filterNot { it in variant.excludes }.forEach { variant.exclude(it) }

  base.filter.includePatterns
      .filterNot { it in variant.filter.includePatterns }
      .forEach {
        variant.filter.includeTestsMatching(it)
      }
  base.filter.excludePatterns
      .filterNot { it in variant.filter.excludePatterns }
      .forEach {
        variant.filter.excludeTestsMatching(it)
      }
  if (variant.filter.isFailOnNoMatchingTests) {
    variant.filter.isFailOnNoMatchingTests = base.filter.isFailOnNoMatchingTests
  }

  if (variant.maxParallelForks == 1) {
    variant.maxParallelForks = base.maxParallelForks
  }
  if (variant.forkEvery == 0L) {
    variant.forkEvery = base.forkEvery
  }
  if (variant.minHeapSize == null) {
    variant.minHeapSize = base.minHeapSize
  }
  if (variant.maxHeapSize == null) {
    variant.maxHeapSize = base.maxHeapSize
  }
  if (!variant.failFast) {
    variant.failFast = base.failFast
  }
  if (!variant.ignoreFailures) {
    variant.ignoreFailures = base.ignoreFailures
  }

  return when (val options = base.options) {
    is JUnitPlatformOptions -> {
      variant.useJUnitPlatform {
        includeTags(*options.includeTags.minus(includeTags).toTypedArray())
        excludeTags(*options.excludeTags.plus(excludedTags).minus(excludeTags).toTypedArray())
        includeEngines(*options.includeEngines.minus(includeEngines).toTypedArray())
        excludeEngines(*options.excludeEngines.minus(excludeEngines).toTypedArray())
      }
      true
    }
    is TestNGOptions -> {
      variant.useTestNG {
        includeGroups(*options.includeGroups.minus(includeGroups).toTypedArray())
        excludeGroups(*options.excludeGroups.plus(excludedTags).minus(excludeGroups).toTypedArray())
      }
      true
    }
    is JUnitOptions -> {
      variant.useJUnit {
        includeCategories(*options.includeCategories.minus(includeCategories).toTypedArray())
        excludeCategories(*options.excludeCategories.minus(excludeCategories).toTypedArray())
      }
      excludedTags.isEmpty()
    }
    else -> excludedTags.isEmpty()
  }
}

/**
 * Appends to [base] the arguments of [own] not already in it, counting repeated ones, so that both a `--add-opens` pair
 * shared by both and another one only in [own] end up in the result once each.
 *
 * @param base arguments of the test task of the source set
 * @param own arguments already set on the variant
 * @return [base] followed by arguments only in [own]
 */
internal fun mergeArguments(base: List<String>, own: List<String>): List<String> {
  val remaining = base.toMutableList()
  return base + own.filterNot { remaining.remove(it) }
}
