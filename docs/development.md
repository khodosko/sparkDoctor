# SparkDoctor Development

## Run Tests

```bash
./gradlew test
```

## Run The CLI Against Fixtures

```bash
./gradlew installDist
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/minimal-eventlog.json --out ./sparkdoctor-report
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/skewed-eventlog.json --out ./sparkdoctor-report
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/shuffle-skewed-eventlog.json --out ./sparkdoctor-report
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/oversized-shuffle-eventlog.json --out ./sparkdoctor-report
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/low-shuffle-parallelism-eventlog.json --out ./sparkdoctor-report
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/spill-heavy-eventlog.json --out ./sparkdoctor-report
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/memory-spill-skew-eventlog.json --out ./sparkdoctor-report
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/tiny-tasks-eventlog.json --out ./sparkdoctor-report
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/retry-waste-eventlog.json --out ./sparkdoctor-report
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/speculation-heavy-eventlog.json --out ./sparkdoctor-report
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/worker-imbalanced-eventlog.json --out ./sparkdoctor-report
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/sql-many-exchanges-eventlog.json --out ./sparkdoctor-report
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/failed-eventlog.json --out ./sparkdoctor-report
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/real-spark-eventlog.json --out ./sparkdoctor-report
```

For development, `./gradlew run` still works:

```bash
./gradlew run --args="analyze src/test/resources/fixtures/minimal-eventlog.json --out ./sparkdoctor-report"
```

## Build A Local Release Archive

```bash
./gradlew distZip
ls build/distributions/
```

GitHub publishes release archives when a version tag is pushed, for example:

```bash
git tag v0.1.2
git push origin v0.1.2
```

## Generate A Real Spark Event Log Fixture

```bash
bash scripts/generate-real-spark-eventlog-fixture.sh
```

This requires `spark-submit` on `PATH`. The script runs a small local Spark job with event logging enabled and writes:

```text
src/test/resources/fixtures/real-spark-eventlog.json
```

Spark 4 may write Zstandard-compressed event logs. The script decompresses those logs with `zstd` when needed and sanitizes machine-specific paths, local application IDs, and user names before writing the fixture.
