# Changelog

All notable changes to SparkDoctor will be documented in this file.

## Unreleased

## 0.1.5 - 2026-07-11

- Corrected public OSS positioning, roadmap status, Java 17 prerequisites, wrapper commands, release installation, and source-checkout usage guidance.
- Replaced the machine-specific Gradle Java path with a single `sparkDoctorVersion` property used by Gradle, the CLI, and additive `analysis.json` producer metadata.
- Added release tag/version enforcement, archive content and embedded-version verification, public-only distribution filtering, and immutable GitHub Actions pins.
- Hardened schema-version-1 documentation and contract tests for required types, nullability, public SQL fields, additive compatibility, and excluded internal plan state.
- Added report-local bottleneck instance IDs and recommendation correlation IDs without renaming or removing existing schema-version-1 fields.
- Rejected empty or non-event inputs, rejected multiple application starts, and selected the highest stage attempt even when older events arrive later.
- Retained the first successful logical task metrics, aggregated failed-attempt evidence across stage attempts, and aligned oversized-shuffle suppression with shuffle-skew eligibility.
- Staged report generation before promotion, rejected unsafe input/output overlap, and cleaned managed partial output after write failures while preserving unrelated files.
- Added validated fixture sanitization for local paths, users, IDs, and private network addresses before atomically replacing the checked-in real Spark event log.
- Added a top-level `schemaVersion` to `analysis.json` and documented the machine-readable output contract for downstream tooling.
- Added fixture-backed contract tests for key `analysis.json` fields used by downstream consumers.
- Suppressed broad duplicate SQL subtree findings when the same SQL execution already has a more specific possible missed exchange reuse finding.
- Added explicit confidence and validation evidence for possible missed exchange reuse findings.
- Tuned possible missed exchange reuse detection to require duplicated subtrees rooted at `Exchange`, reducing noise from broader AQE or wrapper subtrees.
- Hardened SQL subtree fingerprinting for `PushedFilters` ordering so equivalent pushed-filter lists from Spark or connectors can match even when filter order is unstable.

## 0.1.4 - 2026-06-12

- Added repeated SQL physical plan subtree detection to flag duplicated plan fragments that may indicate duplicated work, cache opportunities, or missed reuse.
- Added possible missed exchange reuse detection for repeated exchange-like SQL plan subtrees.
- Documented SQL subtree diagnostics, evidence fields, and repeated-subtree output in `sql-executions.md`.

## 0.1.3 - 2026-06-09

- Fixed Spark 4 event-log directory analysis for `eventlog_v2_*` application directories and parent event-log directories.
- Documented the Spark 4 event-log directory layout and Graphviz commands for rendering SQL plan DOT files.
- Improved SQL report readability with grouped operator counts, better DOT labels, and human-readable recommendation evidence values.

## 0.1.2 - 2026-06-07

- Improved recommendation wording for spill pressure, shuffle partition skew, retry waste, oversized shuffle partitions, low shuffle parallelism, and failed stages.
- Added human-readable evidence in key recommendations, including MiB/GiB byte values, seconds, task counts, failed attempt duration, and threshold context.

## 0.1.1 - 2026-06-06

- Shortened the README into a quick-start project overview for new users.
- Moved detailed detection rules, event-log discovery guidance, output interpretation, and development instructions into `docs/`.
- Updated release archives to include project docs, contribution guidance, and roadmap files so README links work in downloaded distributions.

## 0.1.0 - 2026-06-06

- Added the local `sparkdoctor analyze` CLI.
- Added local Spark event-log parsing for files and directories.
- Added streaming event-log parsing so large logs are not loaded into memory at once.
- Added support for plain, gzip, Zstandard, LZ4, and Snappy event logs.
- Added `analysis.json`, `recommendations.md`, `sql-executions.md`, and SQL plan DOT outputs.
- Added task duration skew, shuffle partition skew, oversized shuffle partition, low shuffle parallelism, spill pressure, memory/disk spill skew, tiny task, retry waste, speculative execution, executor/host imbalance, SQL exchange, failed job, and failed stage detections.
- Added stage hotspots, bottleneck evidence, severity summaries, and actionable parse-failure guidance.
- Added real Spark-generated fixture coverage.
- Added Apache 2.0 license, contribution docs, issue templates, CI, and public roadmap.
