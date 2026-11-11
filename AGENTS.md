# Agent Instructions - Downshift Gradle Plugin

Gradle plugin (`io.github.malczuuu.downshift`) running already compiled tests against older lines of BOM-managed
dependencies: for each declared line of each BOM (and each combination of lines), registers a `Test` task rerunning the
same test classes on a runtime classpath with that BOM replaced by the latest released patch of the line.

## Build & Validate

- Default tasks: `./gradlew` runs `spotlessApply build` (format, compile, unit test).
- `./gradlew test --tests "*.VersionLineTest"` - single test class/method.
- `./gradlew integrationTest` - TestKit compat tests; `-Pcompat.gradle.version=9.0.0` targets a specific Gradle. Must
  include a test project with every feature turned on at once, kept mirrored test-for-test between
  `KotlinDslCompatibilityTest` and `GroovyDslCompatibilityTest`.
- `./gradlew allTest` - unit + integration.
- Java 21 toolchain builds the plugin; compiled output targets Java 8 bytecode.
- Functional and integration tests resolve real BOMs (Jackson, Log4j) from Maven Central.

## Project Layout

| Path                                     | Contents                                         |
|------------------------------------------|--------------------------------------------------|
| `src/main/kotlin/.../downshift`          | `DownshiftPlugin` entry point                    |
| `src/main/kotlin/.../downshift/dsl`      | `downshift { }` extension and specs              |
| `src/main/kotlin/.../downshift/internal` | internal plugin logic (lines, swaps, tags)       |
| `src/test/kotlin`                        | Unit + Gradle TestKit functional tests           |
| `src/integrationTest/kotlin`             | Tests for Gradle compatibility                   |
| `build.gradle.kts`                       | Project and build config                         |

## Architecture

- `DownshiftPlugin` wires everything once the `java` plugin is applied: for each opted-in source set (`test` by
  default) registers an aggregate `<sourceSet>Downshift` task and, per platform line and per combination, a dependency
  scope, a resolvable runtime classpath and a `Test` task.
- `Swap` is a BOM replaced by the latest patch of a line within one task; a platform line has one, a combination
  several. It also computes excluded tags.
- `VersionLine` is a `major.minor` line; `TagExclusions.kt` builds tags (`<platform><major>.<minor>+`) and the tags to
  exclude for a tested line.
- `DownshiftExtension` is the `downshift { }` DSL, with:
  - `DownshiftPlatformSpec` - BOM, its lines and settings,
  - `DownshiftCombinationSpec` - lines of several platforms tested together.

## Coding Rules

- Let `spotlessApply` handle formatting - never format manually.
- Follow existing code patterns and naming conventions.
- Keep everything lazy: tasks and configurations are registered, never created eagerly, and DSL values are read only
  when tasks are configured.
