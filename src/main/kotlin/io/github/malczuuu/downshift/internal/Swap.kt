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

/**
 * A BOM replaced by another version on the runtime classpath of a single test task.
 *
 * @property group group of the BOM module
 * @property module name of the BOM module
 * @property version version the tests run against
 * @property enforced whether the BOM is applied as an enforced platform
 */
internal data class Swap(val group: String, val module: String, val version: VersionSelector, val enforced: Boolean) {

  /** Dependency notation of the BOM at [version], e.g. `group:module:4.0.+`. */
  val notation: String
    get() = "$group:$module:${version.notation}"

  /**
   * Whether this swap replaces the module identified by [group] and [module].
   *
   * @param group group of the module
   * @param module name of the module
   * @return `true` if it is the BOM of this swap
   */
  fun matches(group: String?, module: String): Boolean = this.group == group && this.module == module
}
