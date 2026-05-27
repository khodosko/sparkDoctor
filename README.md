# SparkDoctor

SparkDoctor is a local-first CLI for analyzing Apache Spark event logs.

It is an Apache Spark event log analyzer and Spark performance diagnostics tool for engineers who want to inspect Spark job performance locally from event logs.

Spark jobs often get slow or expensive because of a few recurring problems: skewed tasks, skewed shuffle partitions, memory pressure, disk spill, and retries that hide the real shape of the workload. SparkDoctor reads Spark event logs and turns those raw execution metrics into a small set of bottlenecks and recommendations.

The goal is straightforward: run one command against a Spark event log and get useful evidence about what likely made the job slow.

## Current Status

SparkDoctor is early-stage and focused on offline event log analysis.

It currently generates:

- `analysis.json`: machine-readable analysis output
- `recommendations.md`: human-readable recommendation summary
- `sql-executions.md`: human-readable SQL execution plan output when SQL events are present
- `sql-execution-<id>.dot`: Graphviz SQL plan graph files with operator names, compact metric labels, and metric values when Spark exposes them
- terminal summary output

Supported inputs:

- plain Spark event log files
- gzip-compressed event logs
- Zstandard-compressed event logs with `.zstd` or `.zst` extensions
- LZ4-compressed event logs with `.lz4` extensions
- Snappy-compressed event logs with `.snappy` extensions
- local directories containing event logs

## What SparkDoctor Analyzes

SparkDoctor parses Spark listener events and builds stage-level metrics from successful task attempts.

Use it as a Spark event log performance analyzer for offline Spark troubleshooting, Spark shuffle skew detection, Spark spill analysis, and Spark task skew diagnostics.

Current parsed events include:

- `SparkListenerApplicationStart`
- `SparkListenerApplicationEnd`
- `SparkListenerJobStart`
- `SparkListenerJobEnd`
- `SparkListenerStageSubmitted`
- `SparkListenerStageCompleted`
- `SparkListenerTaskEnd`
- `SparkListenerSQLExecutionStart`
- `SparkListenerSQLAdaptiveExecutionUpdate`
- `SparkListenerSQLExecutionEnd`

Current summary and stage metrics include:

- completed task count
- completed and failed job counts
- completed and failed stage counts
- failed job details
- failed stage details and failure reasons
- SQL execution descriptions, timing, and physical plan text
- min, max, and average task duration
- total shuffle read bytes
- max task shuffle read bytes
- median, p95, and p99 task shuffle read bytes
- per-task shuffle read distribution
- total memory bytes spilled
- total disk bytes spilled
- max task memory bytes spilled
- max task disk bytes spilled

Failed task attempts are ignored for stage metric aggregation. Successful task attempts are deduplicated by stage ID, stage attempt ID, and task index so retries and speculative attempts do not inflate duration, shuffle, or spill metrics.

## Current Bottleneck Rules

### Task Duration Skew

Reports `task_duration_skew` when:

- stage has at least 10 completed tasks
- max task duration is at least 3x the average task duration

Example:

```text
avgTaskDurationMillis = 1800
maxTaskDurationMillis = 9000
skewRatio = 5.0
```

This usually means one or more tasks are much slower than the rest. Possible causes include data skew, uneven partition sizes, executor imbalance, spills, or locality problems.

### Shuffle Partition Skew

Reports `shuffle_partition_skew` when:

- stage has at least 10 shuffle-reading tasks
- max task shuffle read bytes is greater than median task shuffle read bytes times 5
- max task shuffle read bytes is greater than 256 MiB

Example:

```text
medianTaskShuffleReadBytes = 10 MiB
maxTaskShuffleReadBytes = 300 MiB
skewRatio = 30.0
```

This is based on the same general idea as Spark AQE skew handling: a partition is suspicious when it is both much larger than the median and large in absolute terms.

### Oversized Shuffle Partitions

Reports `oversized_shuffle_partitions` when shuffle-reading tasks are processing large partitions even if the stage is not skewed.

This is different from `shuffle_partition_skew`: skew means one or a few partitions are much larger than typical tasks, while oversized partitions means typical shuffle partitions are already large.

Reports when:

- stage has at least 2 shuffle-reading tasks
- p95 task shuffle read bytes is at least 256 MiB
- or max task shuffle read bytes is at least 2 GiB
- and the stage does not look like shuffle partition skew

Severity is `high` when:

- p95 task shuffle read bytes is at least 1 GiB
- or max task shuffle read bytes is at least 2 GiB

Example:

```text
p95TaskShuffleReadBytes = 300 MiB
maxTaskShuffleReadBytes = 300 MiB
severity = medium
```

### Low Shuffle Parallelism

Reports `low_shuffle_parallelism` when a stage reads a large amount of shuffle data with relatively few shuffle-reading tasks.

This is different from oversized shuffle partitions: low parallelism means the stage likely needs more shuffle tasks overall, while oversized partitions means individual task partition sizes are already too large.

Reports when:

- total shuffle read bytes is at least 1 GiB
- shuffle-reading task count is at most 7
- average shuffle read per task is below the oversized partition threshold

Severity is `high` when:

- total shuffle read bytes is at least 10 GiB
- and shuffle-reading task count is at most 15

Example:

```text
shuffleReadBytes = 1.2 GiB
shuffleReadingTasks = 6
avgTaskShuffleReadBytes = 200 MiB
severity = medium
```

### Spill Pressure

Reports `spill_pressure` when a stage spills enough data to suggest memory pressure during shuffle, sort, join, or aggregation.

For stages with at least 2 completed tasks:

- medium if total disk spill is at least 256 MiB
- medium if total memory spill is at least 1 GiB

For single-task stages:

- report only severe cases:
  - total disk spill is at least 1 GiB
  - or total memory spill is at least 4 GiB

Severity is `high` when:

- total disk spill is at least 1 GiB
- or max task disk spill is at least 512 MiB

Example:

```text
diskBytesSpilled = 300 MiB
memoryBytesSpilled = 128 MiB
severity = medium
```

### Failed Jobs And Stages

Reports `failed_job` or `failed_stage` when Spark listener completion events show a failed job or stage.

Severity is `high` because a failed job or stage usually means the Spark application did not finish the intended work or paid retry/recovery cost.

Example:

```text
jobsFailed = 1
stagesFailed = 1
failureReason = Fetch failed
```

## Requirements

- Java 17
- Gradle

Check your setup:

```bash
java -version
gradle -v
```

## Install

Clone the repository:

```bash
git clone https://github.com/khodosko/sparkDoctor.git
cd sparkDoctor
```

Run tests:

```bash
gradle test
```

Install the local CLI launcher:

```bash
gradle installDist
```

Add the generated launcher to your current shell:

```bash
export PATH="$PWD/build/install/sparkdoctor/bin:$PATH"
```

Now verify the command is available:

```bash
sparkdoctor --help
sparkdoctor analyze --help
```

## Usage

Analyze a Spark event log:

```bash
sparkdoctor analyze path/to/eventlog --out ./sparkdoctor-report
```

`--out` is an `analyze` option. Run `sparkdoctor analyze --help` to see analyze-specific options.

Example with the included fixture:

```bash
sparkdoctor analyze src/test/resources/fixtures/spill-heavy-eventlog.json --out ./sparkdoctor-report
```

If you have not added SparkDoctor to `PATH`, run the generated launcher directly:

```bash
./build/install/sparkdoctor/bin/sparkdoctor analyze path/to/eventlog --out ./sparkdoctor-report
```

Output files:

```text
sparkdoctor-report/
  analysis.json
  recommendations.md
  sql-executions.md  # only when SQL execution events are present
  sql-execution-0.dot  # only when structured SQL plan data is present
```

## Example Terminal Output

```text
SparkDoctor analyzed src/test/resources/fixtures/spill-heavy-eventlog.json
Application: spill_heavy_customer_etl
Application ID: app-spill-heavy-0001
Duration: 10000 ms
Jobs: 1
Jobs completed: 0
Jobs failed: 0
Stages: 1
Stages completed: 0
Stages failed: 0
Tasks: 2
SQL executions: 0
Issues detected: 1
Recommendations: 1
Top bottlenecks:
- [medium] spill_pressure (stage 9): Stage 9 has spill pressure.
Output directory: ./sparkdoctor-report
Analysis JSON: ./sparkdoctor-report/analysis.json
Recommendations Markdown: ./sparkdoctor-report/recommendations.md
```

## Example `analysis.json`

```json
{
  "summary": {
    "jobs": 1,
    "jobsCompleted": 0,
    "jobsFailed": 0,
    "stages": 1,
    "stagesCompleted": 0,
    "stagesFailed": 0,
    "tasks": 2,
    "issuesDetected": 1
  },
  "stages": [
    {
      "id": 9,
      "name": "spill-heavy aggregate",
      "completedTasks": 2,
      "shuffleReadBytes": 2000,
      "memoryBytesSpilled": 134217728,
      "diskBytesSpilled": 314572800,
      "maxTaskDiskBytesSpilled": 209715200
    }
  ],
  "sqlExecutions": [],
  "failedJobs": [],
  "failedStages": [],
  "bottlenecks": [
    {
      "type": "spill_pressure",
      "severity": "medium",
      "stageId": 9,
      "message": "Stage 9 has spill pressure."
    }
  ],
  "recommendations": [
    {
      "id": "reduce-spill-pressure",
      "severity": "medium",
      "title": "Reduce spill pressure",
      "relatedBottleneckType": "spill_pressure",
      "stageId": 9
    }
  ]
}
```

## How To Read The Output

Start with the terminal summary:

- `Issues detected` tells you how many bottlenecks were found.
- `Recommendations` tells you how many actions SparkDoctor generated.
- `Top bottlenecks` shows the first few issues with severity and stage ID.

Then open `recommendations.md` for a readable explanation.

Open `sql-executions.md` when SQL executions are present and you want the full physical plan text without JSON escaping.

Open `sql-execution-<id>.dot` with a Graphviz-compatible viewer when structured SQL plan data is present. DOT labels include Spark SQL operator names, simple plan strings, and compact metric labels. Metric values are included when Spark exposes matching SQL accumulator values in the event log.

Use `analysis.json` when you want the raw evidence:

- look at `stages` for task duration, shuffle, and spill metrics
- look at `sqlExecutions` for SQL descriptions, timings, and plan text
- look at `bottlenecks` for detected issues and evidence thresholds
- look at `recommendations` for suggested next actions

## Known Limitations

SparkDoctor is not a complete Spark UI replacement yet.

Current limitations:

- Most detector fixtures are synthetic event logs, though the parser also has coverage for a real Spark-generated event log.
- SQL execution parsing is basic; DOT graph export is available when `sparkPlanInfo` exists, but SQL-specific bottleneck rules are not implemented yet.
- Executor imbalance detection is not implemented yet.
- Detector thresholds are conservative and will change as more real workloads are tested.

If SparkDoctor misses a real issue or reports something incorrect, please include:

- Spark version
- deployment environment, for example local Spark, EMR, Databricks, Kubernetes, or YARN
- the command you ran
- `analysis.json`
- `recommendations.md`
- a small sanitized event log if possible
- what you expected SparkDoctor to report

## Development

Run tests:

```bash
gradle test
```

Run the CLI against fixtures:

```bash
gradle installDist
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/minimal-eventlog.json --out ./sparkdoctor-report
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/skewed-eventlog.json --out ./sparkdoctor-report
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/shuffle-skewed-eventlog.json --out ./sparkdoctor-report
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/oversized-shuffle-eventlog.json --out ./sparkdoctor-report
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/low-shuffle-parallelism-eventlog.json --out ./sparkdoctor-report
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/spill-heavy-eventlog.json --out ./sparkdoctor-report
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/failed-eventlog.json --out ./sparkdoctor-report
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/real-spark-eventlog.json --out ./sparkdoctor-report
```

For development, `gradle run` still works:

```bash
gradle run --args="analyze src/test/resources/fixtures/minimal-eventlog.json --out ./sparkdoctor-report"
```

Generate a real Spark event log fixture:

```bash
bash scripts/generate-real-spark-eventlog-fixture.sh
```

This requires `spark-submit` on `PATH`. The script runs a small local Spark job with event logging enabled and writes:

```text
src/test/resources/fixtures/real-spark-eventlog.json
```

Spark 4 may write Zstandard-compressed event logs. The script decompresses those logs with `zstd` when needed and sanitizes machine-specific paths, local application IDs, and user names before writing the fixture.

## Contributing

SparkDoctor is open source, and contributions are welcome.

Before opening a pull request:

- Add or update unit tests for every behavior change.
- Run `gradle test`.
- Keep changes focused and explain the Spark behavior being parsed, detected, or reported.
- Do not weaken, skip, or delete existing tests to make a change pass.

All contributions require maintainer review and approval before merge.

## Roadmap

See [ROADMAP.md](ROADMAP.md) for the open-source CLI roadmap.

## License

Apache License 2.0. See [LICENSE](LICENSE).
