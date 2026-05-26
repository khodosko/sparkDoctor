# SparkDoctor Roadmap

This roadmap is focused on the open-source SparkDoctor CLI.

SparkDoctor's open-source goal is to make local Apache Spark event log analysis useful without requiring a Spark History Server, agents, or an observability backend.

## Current Focus

SparkDoctor currently focuses on offline event log analysis:

- parse local Spark event logs
- stream large event logs without loading all lines up front
- support plain, gzip, zstd, lz4, and snappy event logs
- summarize Spark applications, jobs, stages, and tasks
- detect common Spark bottlenecks
- generate `analysis.json`
- generate `recommendations.md`
- provide a terminal summary for quick inspection

## Near-Term

These are the next open-source priorities.

### More Spark Diagnostics

- Detect oversized shuffle partitions.
- Detect low shuffle parallelism.
- Detect too many tiny tasks.
- Detect retry-heavy or speculation-heavy workloads.
- Detect executor or host imbalance when event-log signals are available.
- Improve failed job and failed stage evidence.

### Better Report Output

- Add a local HTML report.
- Rank stages by duration, shuffle read, spill, and failure status.
- Add evidence tables for bottlenecks.
- Add a clearer severity summary.
- Make recommendation wording more specific to the observed evidence.

### Better Spark Compatibility

- Add more real Spark-generated fixtures.
- Add fixtures from common environments when sanitized examples are available.
- Improve support for rolling event log directories.
- Parse more Spark listener events that are useful for diagnostics.

### Easier Installation

- Add a Gradle wrapper.
- Publish versioned GitHub releases.
- Provide downloadable CLI archives.
- Document common installation paths.

## Later

These are useful, but not the immediate focus.

- Databricks event log export documentation.
- EMR/S3 event log documentation.
- Cloud storage inputs for S3, GCS, and Azure Blob Storage.
- Run-to-run comparison.
- Richer Spark SQL execution analysis.
- CI usage examples for detecting Spark job performance regressions.

## Contribution Areas

Contributions are especially useful when they include tests and small fixtures.

Good contribution candidates:

- new detector with a focused fixture
- parser support for another Spark listener event
- sanitized real-world event log fixture
- clearer recommendation text
- CLI/report usability improvements
- documentation for a Spark deployment environment

Every behavior change should include unit tests.
