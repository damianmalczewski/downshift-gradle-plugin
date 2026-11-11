# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog][keepachangelog], and this project adheres to [Semantic Versioning][semver].

## [Unreleased]

### Added

- Add `io.github.malczuuu.downshift` plugin running already compiled tests against older lines of BOM-managed
  dependencies.
- Add `downshift { platform("...") { } }` declaring a BOM (`bom`) and its lines (`line(...)`, `lines(...)`), each line
  getting a `Test` task (e.g. `testDownshiftSpringBoot4_0`) running on the latest released patch of that line.
- Add `enforced` (default `true`) applying the BOM as an enforced platform, or as a regular one when `false`.
- Skip tests tagged for newer lines, with tags built from the platform name and the first line needed (e.g.
  `@Tag("springBoot4.1+")`). The line the project is compiled against is detected from the declared BOM version, or
  set with `currentLine`.
- Add `downshift { combination("...") { line("<platform>", "<line>") } }` running tests against lines of several
  platforms at once, in a single task (e.g. `testDownshiftLegacyStack`).
- Add `downshift { sourceSet("...") }` opting other source sets (e.g. JVM test suites) in, on top of `test`.
- Add aggregate `testDownshift` task per source set, attached to `check` unless `attachToCheck = false`.
- Validate usage of Gradle `8.4` or later explicitly, failing with an explicit message below it.

[keepachangelog]: https://keepachangelog.com/en/1.1.0/

[semver]: https://semver.org/spec/v2.0.0.html
