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
- terminal summary output

Supported inputs:

- plain Spark event log files
- gzip-compressed event logs
- local directories containing event logs

## What SparkDoctor Analyzes

SparkDoctor parses Spark listener events and builds stage-level metrics from successful task attempts.

Use it as a Spark event log performance analyzer for offline Spark troubleshooting, Spark shuffle skew detection, Spark spill analysis, and Spark task skew diagnostics.

Current parsed events include:

- `SparkListenerApplicationStart`
- `SparkListenerApplicationEnd`
- `SparkListenerJobStart`
- `SparkListenerStageSubmitted`
- `SparkListenerTaskEnd`

Current stage metrics include:

- completed task count
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
```

## Example Terminal Output

```text
SparkDoctor analyzed src/test/resources/fixtures/spill-heavy-eventlog.json
Application: spill_heavy_customer_etl
Application ID: app-spill-heavy-0001
Duration: 10000 ms
Jobs: 1
Stages: 1
Tasks: 2
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
    "stages": 1,
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

Use `analysis.json` when you want the raw evidence:

- look at `stages` for task duration, shuffle, and spill metrics
- look at `bottlenecks` for detected issues and evidence thresholds
- look at `recommendations` for suggested next actions

## Known Limitations

SparkDoctor is not a complete Spark UI replacement yet.

Current limitations:

- Most detector fixtures are synthetic event logs, though the parser also has coverage for a real Spark-generated event log.
- SQL execution plan analysis is not implemented yet.
- Stage completed and job end events are not fully modeled yet.
- Executor imbalance detection is not implemented yet.
- Low parallelism and partition sizing detectors are not implemented yet.
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
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/spill-heavy-eventlog.json --out ./sparkdoctor-report
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

Near-term:

- parse stage completed and job end events
- detect low parallelism and partition sizing problems
- generate a local HTML report

Longer-term:

- local event log watcher mode
- richer SQL execution analysis
- executor imbalance detection
- run-to-run comparison

## License

Apache License 2.0. See [LICENSE](LICENSE).
