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

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.Provider

/** Values of the `org.gradle.category` attribute of BOMs, applied as regular or enforced platforms. */
private val PLATFORM_CATEGORIES = setOf("platform", "enforced-platform")

/** Characters of version ranges and dynamic versions, which a BOM constraint is not compared against. */
private val NON_EXACT_VERSION_CHARACTERS = charArrayOf('[', ']', '(', ')', ',', '+')

/**
 * Fails a test task before it runs if versions of an enforced swap were overridden on its classpath, which would make
 * it silently pass on versions it was not meant to test. Mechanisms rewriting versions on every configuration do that,
 * e.g. the `io.spring.dependency-management` plugin, `resolutionStrategy.force(...)` or dependency substitution rules,
 * and so does another enforced swap managing the same modules (BOMs imported by a Maven BOM are inlined into it, so
 * `spring-boot-dependencies` manages Jackson modules itself).
 *
 * A class rather than a lambda, so that the configuration cache can store it.
 *
 * @param swaps BOMs replaced on the classpath of the task
 * @param rootComponent root of the resolved classpath of the task
 */
internal class EnforcedVersionsCheck(
    private val swaps: List<Swap>,
    private val rootComponent: Provider<ResolvedComponentResult>,
) : Action<Task> {

  override fun execute(task: Task) {
    val overridden = findOverriddenVersions(rootComponent.get(), swaps)
    if (overridden.isNotEmpty()) {
      throw GradleException(
          "downshift: task '${task.path}' must run against " +
              "${swaps.filter { it.enforced }.joinToString(", ") { it.notation }}, but versions managed by it were " +
              "overridden on its classpath: ${overridden.joinToString(", ")}. Either another enforced platform of " +
              "the same combination manages them too, or another rule rewrites these versions, e.g. the " +
              "io.spring.dependency-management plugin, resolutionStrategy.force(...) or a dependency substitution. " +
              "Set enforced = false on one of the platforms, declare the BOM with platform(...) instead of another " +
              "mechanism, or set enforced = false to accept versions resolved otherwise.",
      )
    }
  }
}

/**
 * Finds modules whose resolved version differs from the one managed by an enforced swap, either by its BOM directly or
 * by BOMs it imports. BOMs of other swaps are skipped, as they are checked on their own.
 *
 * @param root root of the resolved graph
 * @param swaps BOMs replaced in the graph
 * @return descriptions of overridden versions, e.g. `org.acme:acme-lib:1.2.0 instead of 1.0.1`
 */
internal fun findOverriddenVersions(root: ResolvedComponentResult, swaps: List<Swap>): List<String> {
  val overridden = mutableListOf<String>()
  val bomsToCheck = ArrayDeque<ResolvedComponentResult>()
  val visited = mutableSetOf<ComponentIdentifier>()

  for (component in allComponents(root)) {
    val module = component.moduleVersion ?: continue
    val swap = swaps.firstOrNull { it.matches(module.group, module.name) } ?: continue
    if (!swap.enforced) {
      continue
    }
    if (!swap.version.accepts(module.version)) {
      overridden.add("${module.group}:${module.name}:${module.version} instead of ${swap.version}")
    }
    bomsToCheck.add(component)
  }

  while (bomsToCheck.isNotEmpty()) {
    val bom = bomsToCheck.removeFirst()
    if (!visited.add(bom.id)) {
      continue
    }
    for (dependency in bom.dependencies.filterIsInstance<ResolvedDependencyResult>()) {
      val selected = dependency.selected
      val selectedModule = selected.moduleVersion ?: continue
      if (swaps.any { it.matches(selectedModule.group, selectedModule.name) }) {
        continue
      }
      if (isPlatform(dependency)) {
        bomsToCheck.add(selected)
        continue
      }
      val requested = dependency.requested as? ModuleComponentSelector ?: continue
      val requestedVersion = requested.version
      if (
          !dependency.isConstraint ||
              requestedVersion.isEmpty() ||
              requestedVersion.any { it in NON_EXACT_VERSION_CHARACTERS }
      ) {
        continue
      }
      if (selectedModule.version != requestedVersion) {
        overridden.add(
            "${selectedModule.group}:${selectedModule.name}:${selectedModule.version} instead of $requestedVersion",
        )
      }
    }
  }
  return overridden.distinct()
}

private fun allComponents(root: ResolvedComponentResult): Sequence<ResolvedComponentResult> = sequence {
  val visited = mutableSetOf<ComponentIdentifier>()
  val queue = ArrayDeque(listOf(root))
  while (queue.isNotEmpty()) {
    val component = queue.removeFirst()
    if (!visited.add(component.id)) {
      continue
    }
    yield(component)
    component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { queue.add(it.selected) }
  }
}

private fun isPlatform(dependency: ResolvedDependencyResult): Boolean {
  val attributes = dependency.resolvedVariant.attributes
  val category = attributes.keySet().firstOrNull { it.name == "org.gradle.category" } ?: return false
  return attributes.getAttribute(category)?.toString() in PLATFORM_CATEGORIES
}

/**
 * Whether [version] is what this selector resolves to: within its line for `major.minor.+`, or equal otherwise.
 *
 * @param version resolved version
 * @return `true` if [version] matches this selector
 */
private fun VersionSelector.accepts(version: String): Boolean =
    if (dynamic) VersionLine.ofVersion(version) == line else version == notation
