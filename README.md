<h1 align="center">Downshift Gradle Plugin</h1>

<p align="center">
  <a href="https://github.com/damianmalczewski/downshift-gradle-plugin/actions/workflows/ci.yml"><img src="https://github.com/damianmalczewski/downshift-gradle-plugin/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://codecov.io/gh/damianmalczewski/downshift-gradle-plugin" ><img src="https://codecov.io/gh/damianmalczewski/downshift-gradle-plugin/graph/badge.svg?token=MBJ4H28VLR"/></a>
  <a href="https://plugins.gradle.org/plugin/io.github.malczuuu.downshift"><img src="https://img.shields.io/gradle-plugin-portal/v/io.github.malczuuu.downshift" alt="Gradle Plugin Portal Version"></a>
  <a href="https://github.com/damianmalczewski/downshift-gradle-plugin/blob/main/LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-green" alt="License"></a>
</p>

Gradle plugin running already compiled tests against older lines of BOM-managed dependencies.

## Table of Contents

- [Why bother with Downshift Plugin](#why-bother-with-downshift-plugin)
- [Installation](#installation)
    - [Tagging Tests for Newer Lines](#tagging-tests-for-newer-lines)
    - [Combinations](#combinations)
    - [Enforced Platforms](#enforced-platforms)
    - [Additional Source Sets](#additional-source-sets)
- [How It Works](#how-it-works)
- [Compatibility](#compatibility)
- [Building](#building)
- [Using Local Snapshot](#using-local-snapshot)
- [License](#license)

## Why bother with Downshift Plugin

A library usually builds against the newest version of a framework (e.g. Spring Boot `4.1`), while its users still run
older ones (e.g. Spring Boot `4.0`). Compiling against the newest version says nothing about whether the library still
works on an older one at runtime - a single call to a method added in `4.1` ends up with `NoSuchMethodError` there.

Applying `io.github.malczuuu.downshift`:

1. **Registers a `Test` task for each declared version of each BOM** (e.g. `testDownshiftSpringBoot4_0`), rerunning the
   test classes that are already compiled, with no second compilation.
2. **Replaces the BOM on the runtime classpath of that task** with that version - the latest released patch of a line
   (`4.0.+`, resolving to e.g. `4.0.9`) or an exact one (`4.0.8`), applied as an enforced platform. Main and test
   sources keep being compiled against the versions the project declares, just as a library jar is compiled once and
   used with different versions by its consumers.
3. **Skips tests tagged for newer lines**, e.g. `@Tag("springBoot4.1+")` on a test using API added in `4.1`.
4. **Attaches every such task to `check`**, so `./gradlew build` covers all declared lines.

With `major.minor.+`, new patches are picked up automatically, with no dependency update bot involved. Exact versions
pin a specific patch, e.g. to keep testing against the oldest patch still supported.

## Installation

Apply plugin in `build.gradle.kts`.

```kotlin
plugins {
    java
    id("io.github.malczuuu.downshift") version "0.1.0"
}

dependencies {
    // the BOM the project is compiled against
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
}

downshift {
    platform("springBoot") {
        bom = "org.springframework.boot:spring-boot-dependencies"
        line("4.0.+")
    }
}
```

Each version of a platform gets a separate task, named after the platform and the version (`4.0.+` gives
`testDownshiftSpringBoot4_0`, `4.0.8` gives `testDownshiftSpringBoot4_0_8`), plus a `testDownshift` task running all of
them:

```sh
./gradlew testDownshiftSpringBoot4_0
./gradlew testDownshift
```

Configuration (all optional, except `bom`):

```kotlin
downshift {
    // false keeps downshift tasks out of check; run them explicitly with testDownshift
    attachToCheck = true

    // name is used in task names and as the prefix of tags (springBoot4.1+)
    platform("springBoot") {
        // BOM module in group:name notation, without a version
        bom = "org.springframework.boot:spring-boot-dependencies"

        // versions in Gradle notation, also as a collection (lines(listOf(...))):
        // major.minor.+ for the latest released patch of a line, or an exact version;
        // several exact versions of the same line are fine, each gets its own task
        lines("3.5.+", "4.0.+", "4.0.0")

        // false applies the BOM as platform(...) instead of enforcedPlatform(...)
        enforced = true

        // line the project is compiled against, only needed when it cannot be detected (see below)
        currentLine = "4.1"
    }
}
```

### Tagging Tests for Newer Lines

Tests relying on API of a newer line than the oldest declared one can't pass on the older lines. Tags are about lines
(`major.minor`) only, whether the tested version is `4.0.+` or an exact `4.0.8`. Tag them with the
platform name and the first line providing that API, followed by `+`:

```java
@Tag("springBoot4.1+")
@Test
void usesApiAddedInSpringBoot41() {
}
```

The task of each line excludes tags of every newer line up to the one the project is compiled against. That line is
detected from the version of the BOM declared for the tested source set (e.g. `testImplementation(platform(...))`), or
taken from `currentLine` if set. Tests use JUnit Platform, so `useJUnitPlatform()` must be the test framework.

Only lines downshift knows about are covered: for each major version from the tested line to the current one, every
minor up to the newest declared or current line of that major. E.g. with lines `3.4` and `4.0` declared and `4.1`
being the current one, tests run against `3.4` skip tags `springBoot4.0+` and `springBoot4.1+`, but not
`springBoot3.5+`, as no `3.5` line is known. Declare the newest line of an older major (here `3.5`) to cover it.

### Combinations

Each task replaces a single BOM, while the others keep the versions the project declares. To run tests against older
lines of several BOMs at once, declare a combination - it gets its own task, `testDownshiftLegacyStack` here:

```kotlin
downshift {
    platform("springBoot") {
        bom = "org.springframework.boot:spring-boot-dependencies"
        line("4.0.+")
    }
    platform("kotlin") {
        bom = "org.jetbrains.kotlin:kotlin-bom"
        enforced = false
    }
    combination("legacyStack") {
        line("springBoot", "4.0.+")
        line("kotlin", "2.2.+")
    }
}
```

A combination may use versions that its platforms don't declare (above, `kotlin` declares none, so it gets no task of its
own). Tests tagged for a newer line of any combined platform are skipped.

Combinations are never generated automatically. A cartesian product of every line of every platform grows quickly, and
most of the resulting sets of versions were never released, nor are used, together.

### Enforced Platforms

By default, the BOM is applied like `enforcedPlatform(...)`, so its versions win over any other one, including versions
declared directly or brought transitively by other dependencies. This mimics what consumers using Spring's
`io.spring.dependency-management` plugin get.

With `enforced = false` the BOM is applied like `platform(...)` and only recommends versions, so a newer one requested
anywhere else wins, as with Gradle's native BOM support.

Two enforced BOMs managing the same module with different versions cannot be resolved together. Spring Boot's BOM,
for example, manages Kotlin, Jackson and JUnit versions, so combining it with an older Kotlin BOM requires
`enforced = false` on one of them.

### Additional Source Sets

Tests of the `test` source set are rerun by default. Opt other source sets in, e.g. a JVM test suite. Their test task
must be named after the source set, as JVM test suites are.

```kotlin
downshift {
    sourceSet("integrationTest")
}
```

Tasks of other source sets are prefixed with their name, e.g. `integrationTestDownshiftSpringBoot4_0` and
`integrationTestDownshift`.

## How It Works

For version `4.0.+` of platform `springBoot` and the `test` source set, the plugin registers:

- `testDownshiftSpringBoot4_0RuntimeOnly` - dependency scope holding
  `enforcedPlatform("org.springframework.boot:spring-boot-dependencies:4.0.+")`,
- `testDownshiftSpringBoot4_0RuntimeClasspath` - resolvable configuration extending what `testRuntimeClasspath`
  extends (`testImplementation`, `testRuntimeOnly`) and the scope above, with the same attributes. Every request for
  the BOM in it is rewritten to `4.0.+`, so the BOM version declared by the project does not conflict with the
  enforced one. For `major.minor.+` only released versions are accepted (no milestones, release candidates or
  snapshots), while an exact version is used as declared,
- `testDownshiftSpringBoot4_0` - `Test` task with the test classes of the `test` task and its classpath, with
  `testRuntimeClasspath` replaced by the configuration above.

Settings applied to the `test` task directly (e.g. `tasks.test { maxHeapSize = "1g" }`) are not copied. Configure them
for all test tasks with `tasks.withType<Test>().configureEach { }` instead.

Coverage tools instrumenting every `Test` task (e.g. Kover) count downshift tasks too. Exclude them with
`downshift.testTaskNames`, a lazy set of names of every `Test` task the plugin registers, if coverage should come from
the regular tests only:

```kotlin
kover {
    currentProject {
        instrumentation {
            disabledForTestTasks.addAll(downshift.testTaskNames)
        }
    }
}
```

## Compatibility

- **Gradle 8.4 or later**
- **Java 8+**
- **JUnit Platform** as the test framework of tested source sets
- **Configuration cache** and **isolated projects** - the plugin only configures the project it is applied to

## Building

```sh
./gradlew                  # calls configured Gradle default tasks (spotlessApply and build)
./gradlew spotlessApply    # format code: ktfmt for *.kt (Kotlin sources), ktlint for *.kts (Gradle buildscript)
./gradlew build            # compiles the plugin and runs its unit tests
./gradlew integrationTest  # TestKit compatibility test; -Pcompat.gradle.version=9.0.0 targets a specific Gradle
```

## Using Local Snapshot

<details>
<summary><b>Expand...</b></summary>

1. Build project to local Maven repository.
   ```sh
   ./gradlew publishToMavenLocal
   ```
2. Add `mavenLocal` to `pluginManagement` in `settings.gradle.kts`.
   ```kt
   pluginManagement {
       repositories {
           gradlePluginPortal()
           mavenCentral()
           mavenLocal()
       }
   }
   ```
3. Apply plugin in `build.gradle.kts`.
   ```kotlin
   plugins {
       java
       id("io.github.malczuuu.downshift") version "0.1.0-SNAPSHOT"
   }
   ```

</details>

## License

This project is licensed under the Apache License, Version 2.0.

This project is not affiliated with, sponsored by, or endorsed by Gradle. All product names, logos, and brands are
property of their respective owners.
