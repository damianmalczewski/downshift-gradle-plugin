# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog][keepachangelog], and this project adheres to [Semantic Versioning][semver].

## [Unreleased]

### Added

- Fail tasks of enforced platforms before running tests if any version managed by the BOM is resolved otherwise, e.g.
  rewritten by the `io.spring.dependency-management` plugin, `resolutionStrategy.force(...)` or another enforced
  platform of the same combination, instead of silently testing other versions.

### Changed

- Copy settings of the source set's test task (JVM arguments, system properties, environment, filters, includes and
  excludes, forks, heap sizes, test framework options) to every downshift task, without duplicating settings applied to
  both. Tags are skipped as JUnit Platform tags or TestNG groups, following the framework of the test task.
- Copy exclusions declared on the original runtime classpath itself.
- Reject platform names ending with a digit, which would run into the line in tags (`log4j2` -> `log4j22.25+`).

### Fixed

- Accept qualified released versions for `major.minor.+`, e.g. `33.4.8-jre` of `guava-bom`, rejecting only versions
  marked as pre-releases (`M1`, `RC1`, `Beta1`, `SNAPSHOT`, `dev`, ...).
- Resolve downshift classpaths regardless of dependency locking, which failed in strict mode for lack of lock state and
  would otherwise pin the latest patch of a line.

## [0.1.0] - 2026-11-11

### Added

- Add `io.github.malczuuu.downshift` plugin running already compiled tests against older lines of BOM-managed
  dependencies.
- Add `downshift { platform("...") { } }` declaring a BOM (`bom`) and its versions (`line(...)`, `lines(...)` taking
  varargs or a collection), each getting a `Test` task. A version is either `major.minor.+` for the latest released
  patch of a line (`testDownshiftSpringBoot4_0` for `4.0.+`) or an exact one (`testDownshiftSpringBoot4_0_8` for
  `4.0.8`).
- Add `enforced` (default `true`) applying the BOM as an enforced platform, or as a regular one when `false`.
- Skip tests tagged for newer lines, with tags built from the platform name and the first line needed (e.g.
  `@Tag("springBoot4.1+")`). The line the project is compiled against is detected from the declared BOM version, or
  set with `currentLine`.
- Add `downshift { combination("...") { line("<platform>", "<version>") } }` running tests against versions of several
  platforms at once, in a single task (e.g. `testDownshiftLegacyStack`).
- Add `downshift { sourceSet("...") }` opting other source sets (e.g. JVM test suites) in, on top of `test`.
- Add aggregate `testDownshift` task per source set, attached to `check` unless `attachToCheck = false`.
- Add `downshift.testTaskNames`, a lazy set of names of every registered `Test` task (e.g. to exclude them from
  coverage).
- Validate usage of Gradle `8.4` or later explicitly, failing with an explicit message below it.
- Support configuration cache and isolated projects.

[keepachangelog]: https://keepachangelog.com/en/1.1.0/

[semver]: https://semver.org/spec/v2.0.0.html
