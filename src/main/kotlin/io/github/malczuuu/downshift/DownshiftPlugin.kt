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

package io.github.malczuuu.downshift

import io.github.malczuuu.downshift.dsl.DownshiftCombinationSpec
import io.github.malczuuu.downshift.dsl.DownshiftExtension
import io.github.malczuuu.downshift.dsl.DownshiftPlatformSpec
import io.github.malczuuu.downshift.internal.Swap
import io.github.malczuuu.downshift.internal.VersionLine
import io.github.malczuuu.downshift.internal.VersionSelector
import io.github.malczuuu.downshift.internal.capitalized
import io.github.malczuuu.downshift.internal.currentLineOf
import io.github.malczuuu.downshift.internal.excludedTagsOf
import io.github.malczuuu.downshift.internal.isStableVersion
import io.github.malczuuu.downshift.internal.requireMinimumGradleVersion
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.util.GradleVersion

/**
 * Runs already compiled tests against older lines of BOM-managed dependencies.
 *
 * Main and test sources are compiled once, against the versions the project declares. For each line of each platform
 * declared in `downshift { }` (and for each combination), a `Test` task reruns the same test classes on a runtime
 * classpath where the BOM is replaced by the latest released patch of that line. This reproduces what consumers of a
 * library do: they run a jar compiled against the newest version of a dependency on an older one.
 */
public class DownshiftPlugin : Plugin<Project> {

  /**
   * Apply this plugin to the given target object.
   *
   * @param target The target object
   */
  public override fun apply(target: Project) {
    requireMinimumGradleVersion(GradleVersion.current())

    val extension = target.extensions.create<DownshiftExtension>("downshift")
    extension.attachToCheck.convention(true)

    target.plugins.withType<JavaPlugin> {
      val javaSourceSets = target.extensions.getByType<JavaPluginExtension>().sourceSets
      extension.sourceSets.all {
        val sourceSetName = this
        javaSourceSets.matching { it.name == sourceSetName }.all { configureSourceSet(target, extension, this) }
      }
    }

    target.afterEvaluate {
      if (plugins.hasPlugin(JavaPlugin::class.java)) {
        val availableNames = extensions.getByType<JavaPluginExtension>().sourceSets.names
        val unknownNames = extension.sourceSets.filterNot { it in availableNames }
        if (unknownNames.isNotEmpty()) {
          throw InvalidUserDataException(
              "downshift: no source set named ${unknownNames.joinToString(", ") { "'$it'" }}. " +
                  "Available: ${availableNames.joinToString(", ")}.",
          )
        }
      }
    }
  }

  private fun configureSourceSet(project: Project, extension: DownshiftExtension, sourceSet: SourceSet) {
    val aggregateTask =
        project.tasks.register(sourceSet.getTaskName(null, "downshift")) {
          group = LifecycleBasePlugin.VERIFICATION_GROUP
          description = "Runs tests of source set '${sourceSet.name}' against every declared line and combination."
        }

    project.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure {
      dependsOn(extension.attachToCheck.map { if (it) listOf(aggregateTask) else emptyList() })
    }

    extension.platforms.all {
      val platform = this
      lines.all {
        val version = this
        registerVariant(
            project,
            extension,
            sourceSet,
            aggregateTask,
            "${platform.name.capitalized()}${version.taskNameSegment}",
            "Runs tests of source set '${sourceSet.name}' against version $version of platform '${platform.name}'.",
            project.provider { listOf(Target(platform, version)) },
        )
      }
    }

    extension.combinations.all {
      val combination = this
      registerVariant(
          project,
          extension,
          sourceSet,
          aggregateTask,
          combination.name.capitalized(),
          "Runs tests of source set '${sourceSet.name}' against versions of combination '${combination.name}'.",
          project.provider { combination.toTargets(extension) },
      )
    }
  }

  private fun registerVariant(
      project: Project,
      extension: DownshiftExtension,
      sourceSet: SourceSet,
      aggregateTask: TaskProvider<Task>,
      variantName: String,
      variantDescription: String,
      targets: Provider<List<Target>>,
  ) {
    val swaps = targets.map { list -> list.map { it.toSwap() } }
    val configurations = project.configurations
    val baseRuntimeClasspath = configurations.getByName(sourceSet.runtimeClasspathConfigurationName)

    val runtimeOnly =
        configurations.dependencyScope(sourceSet.getTaskName(null, "downshift${variantName}RuntimeOnly")) {
          dependencies.addAllLater(
              swaps.map { list ->
                list.map { swap ->
                  if (swap.enforced) {
                    project.dependencies.enforcedPlatform(swap.notation)
                  } else {
                    project.dependencies.platform(swap.notation)
                  }
                }
              },
          )
        }

    val runtimeClasspath =
        configurations.resolvable(sourceSet.getTaskName(null, "downshift${variantName}RuntimeClasspath")) {
          // The original runtime classpath is not extended itself, but its parents are, so that the BOM declared by
          // the project (e.g. in testImplementation) is still on this one and can be rewritten below.
          extendsFrom(*baseRuntimeClasspath.extendsFrom.toTypedArray())
          extendsFrom(runtimeOnly.get())
          // Realized only once its task is configured, so attributes added by other plugins are already there.
          baseRuntimeClasspath.attributes.keySet().forEach {
            copyAttribute(project, baseRuntimeClasspath.attributes, attributes, it)
          }

          // A BOM declared by the project with its own version would conflict with the strict one from runtimeOnly,
          // so every request for the BOM is pointed to the same line.
          resolutionStrategy.eachDependency {
            swaps
                .get()
                .firstOrNull { it.matches(requested.group, requested.name) }
                ?.let { useVersion(it.version.notation) }
          }
          // The latest patch of a line must be a released version, not a milestone or a release candidate. Exact
          // versions are used as declared.
          resolutionStrategy.componentSelection.all {
            val swap = swaps.get().firstOrNull { it.matches(candidate.group, candidate.module) }
            if (swap != null && swap.version.dynamic && !isStableVersion(candidate.version)) {
              reject("downshift: ${candidate.version} is not a released version")
            }
          }
        }

    val baseTestTask = project.tasks.withType<Test>().named(sourceSet.name)
    val variantTask =
        project.tasks.register<Test>(sourceSet.getTaskName(null, "downshift$variantName")) {
          group = LifecycleBasePlugin.VERIFICATION_GROUP
          description = variantDescription

          val baseTest = baseTestTask.get()
          testClassesDirs = baseTest.testClassesDirs
          classpath = baseTest.classpath.minus(baseRuntimeClasspath).plus(runtimeClasspath.get())

          val declaredDependencies = baseRuntimeClasspath.allDependencies
          val excludedTags = targets.get().flatMap { it.excludedTags(declaredDependencies, sourceSet.name) }.distinct()
          useJUnitPlatform { excludeTags(*excludedTags.toTypedArray()) }

          shouldRunAfter(baseTestTask)
        }

    aggregateTask.configure { dependsOn(variantTask) }
    extension.testTaskNamesProperty.add(variantTask.name)
  }

  private fun <T : Any> copyAttribute(
      project: Project,
      source: AttributeContainer,
      target: AttributeContainer,
      key: Attribute<T>,
  ) {
    target.attributeProvider(key, project.provider { source.getAttribute(key) })
  }

  /** A version of a platform that tests of a single task run against. */
  private data class Target(val platform: DownshiftPlatformSpec, val version: VersionSelector) {

    fun toSwap(): Swap {
      val (group, module) = platform.bomModule()
      return Swap(group, module, version, platform.enforced.get())
    }

    fun excludedTags(declaredDependencies: Iterable<Dependency>, sourceSetName: String): List<String> {
      val (group, module) = platform.bomModule()
      val configuredLine = platform.currentLine.orNull?.let { VersionLine.parse(it) }
      val currentLine = currentLineOf(platform.name, group, module, configuredLine, declaredDependencies, sourceSetName)
      return excludedTagsOf(platform.name, version.line, currentLine, platform.lines.map { it.line }.distinct())
    }
  }

  private fun DownshiftCombinationSpec.toTargets(extension: DownshiftExtension): List<Target> {
    val combinationLines = lines.get()
    if (combinationLines.isEmpty()) {
      throw InvalidUserDataException(
          "downshift: combination '$name' has no lines, add them with " +
              "downshift { combination(\"$name\") { line(\"<platform>\", \"<major.minor.+>\") } }.",
      )
    }
    return combinationLines.map { (platformName, version) ->
      val platform =
          extension.platforms.findByName(platformName)
              ?: throw InvalidUserDataException(
                  "downshift: combination '$name' refers to undeclared platform '$platformName'. " +
                      "Declared: ${extension.platforms.names.joinToString(", ")}.",
              )
      Target(platform, version)
    }
  }
}

/**
 * Splits the `group:name` notation of the BOM of this platform.
 *
 * @return group and name of the BOM module
 * @throws InvalidUserDataException if the BOM is not set or not in `group:name` notation
 */
private fun DownshiftPlatformSpec.bomModule(): Pair<String, String> {
  val notation =
      bom.orNull
          ?: throw InvalidUserDataException(
              "downshift: platform '$name' has no bom, set it with " +
                  "downshift { platform(\"$name\") { bom = \"<group>:<name>\" } }.",
          )
  val parts = notation.split(':')
  if (parts.size != 2 || parts.any { it.isBlank() }) {
    throw InvalidUserDataException(
        "downshift: bom of platform '$name' must be in 'group:name' notation without a version, but was: " +
            "\"$notation\".",
    )
  }
  return parts[0] to parts[1]
}
