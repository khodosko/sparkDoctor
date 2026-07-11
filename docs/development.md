# SparkDoctor Development

Run all commands in this guide from the root of a SparkDoctor source checkout. Java 17 is required; Gradle itself does not need to be installed because the repository includes the Gradle wrapper.

## Run Tests

```bash
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
./gradlew test
```

Python 3 is used only for repository fixture and release tooling; it is not required to run the packaged SparkDoctor CLI.

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
VERSION="$(sed -n 's/^sparkDoctorVersion=//p' gradle.properties)"
./gradlew distZip
python3 scripts/verify_release.py --archive "build/distributions/sparkdoctor-${VERSION}.zip"
```

## Prepare A GitHub Release

GitHub publishes a release only when the pushed tag exactly matches the non-SNAPSHOT `sparkDoctorVersion` in `gradle.properties`. Before tagging, update that property from the development version to the intended release version, finalize `CHANGELOG.md`, commit those release-preparation changes, and confirm the working tree is clean.

From a clean source checkout, run:

```bash
VERSION="$(sed -n 's/^sparkDoctorVersion=//p' gradle.properties)"
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
./gradlew clean test distZip
python3 scripts/verify_release.py --tag "v${VERSION}" --archive "build/distributions/sparkdoctor-${VERSION}.zip"
git push origin main
git tag -a "v${VERSION}" -m "Release SparkDoctor ${VERSION}"
git push origin "v${VERSION}"
```

Create and push the release tag only after the release-preparation commit has been pushed successfully to public `main`. The release workflow rejects a tag whose commit is not reachable from `origin/main`.

Archive-only verification accepts the current development SNAPSHOT and checks names, public contents, forbidden paths, and the embedded application version. When `--tag` is supplied for a release, verification also rejects SNAPSHOT versions, tag/version mismatches, stale README installation versions, and missing dated changelog headings.

After the release, update `sparkDoctorVersion` to the next development `-SNAPSHOT` version in a separate commit.

## Generate A Real Spark Event Log Fixture

```bash
bash scripts/generate-real-spark-eventlog-fixture.sh
```

This requires `spark-submit` on `PATH`. The script runs a small local Spark job with event logging enabled and writes:

```text
src/test/resources/fixtures/real-spark-eventlog.json
```

Spark 4 may write Zstandard-compressed event logs. The script decompresses those logs with `zstd` when needed and validates sanitization of machine-specific paths, local application IDs, user names, and private network addresses before atomically replacing the fixture.
