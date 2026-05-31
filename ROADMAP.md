# SparkDoctor Roadmap

This roadmap is focused on the open-source SparkDoctor CLI.

SparkDoctor's open-source goal is to make local Apache Spark event log analysis useful without requiring a Spark History Server, agents, or an observability backend.

## Current Focus

SparkDoctor currently focuses on offline event log analysis:

- parse local Spark event logs
- stream large event logs without loading all lines up front
- support plain, gzip, zstd, lz4, and snappy event logs
- summarize Spark applications, jobs, stages, and tasks
- summarize Spark SQL executions when SQL listener events are available
- detect common Spark bottlenecks
- detect task-level memory and disk spill skew
- detect excessive tiny task overhead
- detect retry waste from failed task attempts
- detect speculation-heavy workloads
- detect executor and host imbalance
- include failed task attempt evidence for failed stages
- summarize SQL physical plan operators and detect plans with many exchanges
- generate `analysis.json`
- generate `recommendations.md` with stage hotspots and bottleneck evidence
- generate `sql-executions.md` when SQL execution plans are present
- generate Graphviz `.dot` files for structured SQL execution plans
- provide a terminal summary for quick inspection
- print actionable parse-failure guidance and clear stale generated report artifacts before each analysis

## Near-Term

These are the next open-source priorities.

### More Spark Diagnostics

- Add more SQL-specific diagnostics from parsed physical plan nodes and metric values.

### Better Report Output

- Add a local HTML report.
- Add a clearer severity summary.
- Make recommendation wording more specific to the observed evidence.
- Annotate SQL DOT graphs with metrics, highlighted bottleneck nodes, and short guidance labels.

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
