# SparkDoctor

Find skew, spills, wasted shuffle work, failed stages, and performance regressions from Spark event logs in seconds.

SparkDoctor is an open-source, local-first CLI for analyzing Apache Spark event logs. It reads Spark listener events and turns raw execution metrics into bottlenecks, evidence, and recommendations without requiring a Spark History Server or hosted observability backend.

```bash
sparkdoctor analyze path/to/eventlog --out ./sparkdoctor-report
```

## What It Does

Spark jobs often get slow or expensive because of recurring problems: skewed tasks, skewed shuffle partitions, memory pressure, disk spill, failed stages, retry waste, bad partition sizing, or SQL plan issues. SparkDoctor helps surface those patterns from event logs.

It currently generates:

- `analysis.json`: machine-readable analysis output
- `recommendations.md`: human-readable stage hotspots, bottleneck evidence, and recommendations
- `sql-executions.md`: readable SQL execution plan output when SQL events are present
- `sql-execution-<id>.dot`: Graphviz SQL plan graph files when Spark exposes structured plan data
- terminal summary output

Current detections include:

- task duration skew
- shuffle partition skew
- oversized shuffle partitions
- low shuffle parallelism
- spill pressure
- memory and disk spill skew
- too many tiny tasks
- retry waste
- heavy speculative execution
- executor and host imbalance
- SQL plans with many exchanges
- failed jobs and stages

For detailed rules, thresholds, parsed events, metrics, and evidence fields, see [docs/detections.md](docs/detections.md).

## Supported Inputs

SparkDoctor currently reads local files and local directories:

- plain Spark event log files
- gzip-compressed event logs
- Zstandard-compressed event logs with `.zstd` or `.zst` extensions
- LZ4-compressed event logs with `.lz4` extensions
- Snappy-compressed event logs with `.snappy` extensions
- local directories containing event logs

For help finding event logs from local Spark, Spark History Server, Databricks, Amazon EMR, or AWS Glue, see [docs/event-logs.md](docs/event-logs.md).

## Install

### From A GitHub Release

Download the latest release archive, unzip it, and run the CLI:

```bash
curl -L -o sparkdoctor-0.1.2.zip https://github.com/khodosko/sparkDoctor/releases/download/v0.1.2/sparkdoctor-0.1.2.zip
unzip sparkdoctor-0.1.2.zip
./sparkdoctor-0.1.2/bin/sparkdoctor --help
```

Optionally add it to your shell:

```bash
export PATH="$PWD/sparkdoctor-0.1.2/bin:$PATH"
```

### Build From Source

Requirements:

- Java 17

```bash
git clone https://github.com/khodosko/sparkDoctor.git
cd sparkDoctor
./gradlew test
./gradlew installDist
./build/install/sparkdoctor/bin/sparkdoctor --help
```

More development commands are in [docs/development.md](docs/development.md).

## Usage

Analyze a Spark event log:

```bash
sparkdoctor analyze path/to/eventlog --out ./sparkdoctor-report
```

Example with an included fixture:

```bash
sparkdoctor analyze src/test/resources/fixtures/spill-heavy-eventlog.json --out ./sparkdoctor-report
```

If you have not added SparkDoctor to `PATH`, run the generated launcher directly:

```bash
./build/install/sparkdoctor/bin/sparkdoctor analyze path/to/eventlog --out ./sparkdoctor-report
```

## Example Output

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
Severity summary: medium=1
Recommendations: 1
Top bottlenecks:
- [medium] spill_pressure (stage 9): Stage 9 has spill pressure.
Output directory: ./sparkdoctor-report
Analysis JSON: ./sparkdoctor-report/analysis.json
Recommendations Markdown: ./sparkdoctor-report/recommendations.md
```

Example `recommendations.md` excerpt:

```text
### Reduce spill pressure

- Severity: medium
- Stage ID: 9
- Related bottleneck: spill_pressure

Evidence:

- completedTasks: 2
- diskBytesSpilled: 314572800
- mediumDiskSpillThresholdBytes: 268435456

Stage 9 spilled 300 MiB to disk and 128 MiB to memory across 2 completed tasks.
```

For output files, `analysis.json` examples, SQL plan output, and report interpretation, see [docs/output.md](docs/output.md).

## Project Docs

- [Detections and thresholds](docs/detections.md)
- [Finding Spark event logs](docs/event-logs.md)
- [Output guide](docs/output.md)
- [Development guide](docs/development.md)
- [Roadmap](ROADMAP.md)
- [Contributing](CONTRIBUTING.md)
- [Changelog](CHANGELOG.md)

## Feedback And Real Event Logs

SparkDoctor is early-stage. If it does not work on your event log, misses a real issue, or reports something incorrect, please open a GitHub issue or email `dkhodosko@gmail.com`.

Useful details include Spark version, deployment environment, input type, compression type, command used, error message, `analysis.json`, `recommendations.md`, and a small sanitized event log if possible.

## Contributing

SparkDoctor is open source, and contributions are welcome.

Before opening a pull request:

- Add or update unit tests for every behavior change.
- Run `./gradlew test`.
- Keep changes focused and explain the Spark behavior being parsed, detected, or reported.
- Do not weaken, skip, or delete existing tests to make a change pass.

All contributions require maintainer review and approval before merge.

## License

Apache License 2.0. See [LICENSE](LICENSE).
