# SparkDoctor Output Guide

SparkDoctor writes local report artifacts into the output directory passed to `--out`.

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
Severity summary: medium=1
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
  "schemaVersion": "1",
  "application": {
    "id": "app-spill-heavy-0001",
    "name": "spill_heavy_customer_etl",
    "durationMillis": 10000
  },
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

## `analysis.json` Contract

`analysis.json` is SparkDoctor's machine-readable output contract. It is intended for scripts, CI checks, and downstream tools that need stable Spark analysis evidence without parsing terminal or Markdown output.

The top-level `schemaVersion` field identifies the contract version. Additive fields may be introduced within the same schema version, but existing key fields should not be renamed, removed, or have their meaning changed casually. If a future change needs to alter existing field names or semantics, it should use a new schema version.

Important stable fields include:

- `application.id`, `application.name`, and `application.durationMillis`
- `summary.jobs`, `summary.jobsCompleted`, `summary.jobsFailed`
- `summary.stages`, `summary.stagesCompleted`, `summary.stagesFailed`
- `summary.tasks` and `summary.issuesDetected`
- `stages[*].id`, `stages[*].name`, completed task counts, task duration summaries, shuffle totals, and spill totals
- `failedJobs` and `failedStages`
- `bottlenecks[*].type`, `bottlenecks[*].severity`, `bottlenecks[*].stageId`, and `bottlenecks[*].evidence`
- `recommendations[*].id`, `recommendations[*].severity`, `recommendations[*].relatedBottleneckType`, and `recommendations[*].stageId`

## How To Read The Output

Start with the terminal summary:

- `Issues detected` tells you how many bottlenecks were found.
- `Severity summary` shows how many detected issues are high, medium, or low severity.
- `Recommendations` tells you how many actions SparkDoctor generated.
- `Top bottlenecks` shows the first few issues with severity and stage ID.

Then open `recommendations.md` for stage hotspots, bottleneck evidence, and a readable explanation.

Open `sql-executions.md` when SQL executions are present and you want operator summaries plus the full physical plan text without JSON escaping. The operator summary starts with grouped counts for common plan features such as Exchanges, Sorts, HashAggregates, Joins, Scans, and AQE nodes, then keeps detailed Spark operator counts below that. When SparkDoctor finds repeated physical plan subtrees, the SQL report also includes a `Repeated Subtrees` section with duplicate counts, subtree size, depth, contained operators, and interesting operators.

Open `sql-execution-<id>.dot` with a Graphviz-compatible viewer when structured SQL plan data is present. DOT labels include Spark SQL operator names, DOT node IDs, shortened simple plan strings, and compact metric labels. Metric values are included when Spark exposes matching SQL accumulator values in the event log.

To render a DOT graph as SVG with Graphviz:

```bash
dot -Tsvg sparkdoctor-report/sql-execution-0.dot -o sparkdoctor-report/sql-execution-0.svg
open sparkdoctor-report/sql-execution-0.svg
```

Use `analysis.json` when you want the raw evidence:

- look at `stages` for task duration, shuffle, and spill metrics
- look at `sqlExecutions` for SQL descriptions, timings, operator summaries, and plan text
- look at `bottlenecks` for detected issues and evidence thresholds
- look at `recommendations` for suggested next actions

If parsing fails, SparkDoctor prints the expected event-log format and supported compression types. It also removes SparkDoctor-generated report artifacts from the output directory so stale output from an earlier successful run is not mistaken for the failed run's result.

## Known Limitations

SparkDoctor is not a complete Spark UI replacement yet.

Current limitations:

- Most detector fixtures are synthetic event logs, though the parser also has coverage for a real Spark-generated event log.
- SQL diagnostics are still early; SparkDoctor currently summarizes plan operators, detects plans with many exchanges, and reports repeated physical plan subtrees. These SQL reuse signals are based on physical plans from event logs; they do not prove analyzer or optimizer behavior without additional context.
- Detector thresholds are conservative and will change as more real workloads are tested.

## Feedback And Real Event Logs

If SparkDoctor does not work on your event log, misses a real issue, or reports something incorrect, please open a GitHub issue or email `dkhodosko@gmail.com`.

Helpful details to include:

- Spark version
- deployment environment, for example local Spark, EMR, Databricks, Kubernetes, or YARN
- whether the input is a file or directory
- compression type if known, for example plain, gzip, zstd, lz4, or snappy
- the command you ran
- the error message, if any
- `analysis.json`
- `recommendations.md`
- a small sanitized event log if possible
- what you expected SparkDoctor to report
