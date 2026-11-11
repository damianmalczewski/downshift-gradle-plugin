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

import io.github.malczuuu.downshift.internal.DEFAULT_SOURCE_SET_NAME
import io.github.malczuuu.downshift.internal.requireValidName
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.kotlin.dsl.newInstance

/**
 * The `downshift { }` DSL, configuring which BOM lines already compiled tests run against:
 * ```
 * downshift {
 *     platform("springBoot") {
 *         bom = "org.springframework.boot:spring-boot-dependencies"
 *         line("4.0.+")
 *     }
 * }
 * ```
 */
@DownshiftDslMarker
public abstract class DownshiftExtension @Inject constructor(private val objects: ObjectFactory) {

  /** Whether the `check` task depends on every task registered by this plugin. Defaults to `true`. */
  public abstract val attachToCheck: Property<Boolean>

  /** Backs [testTaskNames], filled by the plugin as it registers tasks. */
  internal val testTaskNamesProperty: SetProperty<String> = objects.setProperty(String::class.java)

  /**
   * Names of every `Test` task registered by this plugin, across all source sets, platform lines and combinations, but
   * not the aggregate `testDownshift` ones. Lazy, so it also covers tasks registered after it is read, e.g. to exclude
   * them from coverage:
   * ```
   * kover {
   *     currentProject {
   *         instrumentation {
   *             disabledForTestTasks.addAll(downshift.testTaskNames)
   *         }
   *     }
   * }
   * ```
   */
  public val testTaskNames: Provider<Set<String>>
    get() = testTaskNamesProperty

  /** Declared platforms, keyed by name. */
  internal val platforms: NamedDomainObjectContainer<DownshiftPlatformSpec> =
      objects.domainObjectContainer(DownshiftPlatformSpec::class.java) { name ->
        requireValidName("platform", name)
        objects.newInstance<DownshiftPlatformSpec>(name).apply { enforced.convention(true) }
      }

  /** Declared combinations, keyed by name. */
  internal val combinations: NamedDomainObjectContainer<DownshiftCombinationSpec> =
      objects.domainObjectContainer(DownshiftCombinationSpec::class.java) { name ->
        requireValidName("combination", name)
        objects.newInstance<DownshiftCombinationSpec>(name)
      }

  /** Names of source sets whose tests are run against older lines. Contains `test` by default. */
  internal val sourceSets: DomainObjectSet<String> =
      objects.domainObjectSet(String::class.java).apply { add(DEFAULT_SOURCE_SET_NAME) }

  /**
   * Declares the platform named [name], or configures it if already declared:
   * ```
   * downshift {
   *     platform("springBoot") {
   *         bom = "org.springframework.boot:spring-boot-dependencies"
   *         lines("3.5.+", "4.0.+")
   *     }
   * }
   * ```
   *
   * @param name name of the platform, alphanumeric and starting with a letter; used in task names and as the prefix of
   *   tags (`@Tag("springBoot4.1+")`)
   * @param configuration action applied to the platform
   */
  public fun platform(name: String, configuration: Action<in DownshiftPlatformSpec>) {
    configuration.execute(platforms.maybeCreate(name))
  }

  /**
   * Declares the combination named [name] of lines of several platforms, or configures it if already declared:
   * ```
   * downshift {
   *     combination("legacyStack") {
   *         line("springBoot", "4.0.+")
   *         line("kotlin", "2.2.+")
   *     }
   * }
   * ```
   *
   * Combinations are never generated automatically, as a cartesian product of every line of every platform grows fast
   * and mostly yields sets of versions never released together.
   *
   * @param name name of the combination, alphanumeric and starting with a letter; used in its task name
   * @param configuration action applied to the combination
   */
  public fun combination(name: String, configuration: Action<in DownshiftCombinationSpec>) {
    configuration.execute(combinations.maybeCreate(name))
  }

  /**
   * Runs tests of the source set named [name] against older lines too, on top of `test`. Its test task must have the
   * same name as the source set, as for JVM test suites (e.g. `integrationTest`):
   * ```
   * downshift {
   *     sourceSet("integrationTest")
   * }
   * ```
   *
   * @param name name of the Gradle `SourceSet`
   */
  public fun sourceSet(name: String) {
    sourceSets.add(name)
  }
}
