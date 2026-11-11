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

package io.github.malczuuu.downshift.fixtures

import java.io.File

/**
 * Fixture writing a file-based Maven repository with POM-only modules: BOMs managing versions of libraries, and the
 * libraries themselves. Enough to resolve dependency graphs offline, without any artifact files.
 */
class LocalMavenRepository(val dir: File) {

  private val versions = mutableMapOf<String, MutableList<String>>()

  /** Writes [group]:[name]:[version] BOM managing [managed] modules (`group:name`) at their versions. */
  fun bom(group: String, name: String, version: String, managed: Map<String, String>) {
    val dependencies =
        managed.entries.joinToString("") { (module, managedVersion) ->
          val (managedGroup, managedName) = module.split(':')
          "<dependency><groupId>$managedGroup</groupId><artifactId>$managedName</artifactId>" +
              "<version>$managedVersion</version></dependency>"
        }
    writePom(
        group,
        name,
        version,
        "<dependencyManagement><dependencies>$dependencies</dependencies></dependencyManagement>",
    )
  }

  /** Writes [group]:[name]:[version] library without dependencies. */
  fun library(group: String, name: String, version: String) {
    writePom(group, name, version, "")
  }

  private fun writePom(group: String, name: String, version: String, body: String) {
    val moduleDir = File(dir, "${group.replace('.', '/')}/$name")
    File(moduleDir, "$version/$name-$version.pom").apply {
      parentFile.mkdirs()
      writeText(
          "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><modelVersion>4.0.0</modelVersion>" +
              "<groupId>$group</groupId><artifactId>$name</artifactId><version>$version</version>" +
              "<packaging>pom</packaging>$body</project>",
      )
    }
    val moduleVersions = versions.getOrPut("$group:$name") { mutableListOf() }.apply { add(version) }
    File(moduleDir, "maven-metadata.xml")
        .writeText(
            "<metadata><groupId>$group</groupId><artifactId>$name</artifactId><versioning><versions>" +
                moduleVersions.joinToString("") { "<version>$it</version>" } +
                "</versions></versioning></metadata>",
        )
  }
}
