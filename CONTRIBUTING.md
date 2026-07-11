# Contributing to SparkDoctor

SparkDoctor is open source, and contributions are welcome.

## Contribution Rules

- Add or update unit tests for every behavior change.
- Run the Python script tests and `./gradlew test` before opening a pull request.
- Keep pull requests focused.
- Explain the Spark behavior being parsed, detected, or reported.
- Do not weaken, skip, delete, or loosen existing tests to make a change pass.
- Existing failing tests should be treated as product or implementation signals first.

All contributions require maintainer review and approval before merge.

## Development Setup

Run all repository-relative commands below from the root of a SparkDoctor source checkout.

Requirements:

- Java 17
- Python 3 for repository script tests and release/fixture tooling
- Gradle is not required when using the checked-in wrapper

Run tests:

```bash
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
./gradlew test
```

Install the local CLI launcher:

```bash
./gradlew installDist
```

Run SparkDoctor:

```bash
./build/install/sparkdoctor/bin/sparkdoctor analyze src/test/resources/fixtures/minimal-eventlog.json --out ./sparkdoctor-report
```

## Pull Request Checklist

Before opening a pull request:

- Tests were added or updated for the change.
- `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` passes.
- `./gradlew test` passes.
- README or documentation was updated if behavior, output, or usage changed.
- The pull request description explains the Spark behavior involved.
- The change does not weaken existing test assertions.
