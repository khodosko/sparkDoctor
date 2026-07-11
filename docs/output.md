# SparkDoctor Output Guide

SparkDoctor writes local report artifacts into the output directory passed to `--out`.

```text
sparkdoctor-report/
  analysis.json
  recommendations.md
  sql-executions.md  # only when SQL execution events are present
  sql-execution-0.dot  # only when structured SQL plan data is present
```

SparkDoctor generates a successful report in a temporary staging directory before promoting its managed artifacts. If parsing or report generation fails, it removes managed SparkDoctor outputs while preserving unrelated files in the output directory; if filesystem permissions prevent cleanup, the CLI reports that explicitly. To protect event-log input, SparkDoctor rejects an output directory that is inside a directory input and rejects a file input that is also a managed output path.

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

This example is abridged for readability. Omitted fields are not necessarily optional; use the contract tables below to determine which fields are always present.

```json
{
  "schemaVersion": "1",
  "producer": {
    "name": "SparkDoctor",
    "version": "0.1.5-SNAPSHOT"
  },
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
      "instanceId": "bottleneck-1",
      "type": "spill_pressure",
      "severity": "medium",
      "stageId": 9,
      "message": "Stage 9 has spill pressure.",
      "evidence": {
        "completedTasks": 2,
        "memoryBytesSpilled": 134217728,
        "diskBytesSpilled": 314572800,
        "maxTaskDiskBytesSpilled": 209715200
      }
    }
  ],
  "recommendations": [
    {
      "id": "reduce-spill-pressure",
      "severity": "medium",
      "title": "Reduce spill pressure",
      "relatedBottleneckType": "spill_pressure",
      "relatedBottleneckId": "bottleneck-1",
      "stageId": 9
    }
  ]
}
```

## `analysis.json` Contract

`analysis.json` is SparkDoctor's machine-readable output contract. It is intended for scripts, CI checks, and downstream tools that need stable Spark analysis evidence without parsing terminal or Markdown output.

The top-level `schemaVersion` field identifies the contract version and is a JSON string. Schema version `"1"` follows these compatibility rules:

- Existing stable fields are not renamed, removed, or given incompatible semantics within schema version 1.
- New top-level fields, nested fields, detector types, and detector-specific `evidence` fields may be added within schema version 1.
- Consumers must ignore unknown fields and unknown detector types rather than rejecting an otherwise valid report.
- A change that removes a stable field or changes its type or meaning requires deliberate schema-version and downstream-compatibility consideration.

### Current Emitter Shape

Current SparkDoctor builds emit these keys. Arrays are present even when empty.

| Field | JSON type | Nullable | Meaning |
| --- | --- | --- | --- |
| `schemaVersion` | string | no | Public analysis contract version; currently `"1"`. |
| `producer` | object | no | SparkDoctor producer identity and executable version. |
| `application` | object | no | Application identity and timing. |
| `summary` | object | no | Application-level counts. |
| `stages` | array | no | Selected stage-attempt analyses. |
| `sqlExecutions` | array | no | SQL execution summaries. |
| `failedJobs` | array | no | Failed job summaries. |
| `failedStages` | array | no | Failed stage summaries. |
| `bottlenecks` | array | no | Detector findings and evidence. |
| `recommendations` | array | no | Recommended actions derived from findings. |

The additive `producer` object was introduced after the `0.1.4` release. Historical schema-version-1 reports can omit it, so consumers that accept schema 1 must tolerate its absence. Current SparkDoctor contract tests require it from the current emitter.

All other fields described below are emitted as keys unless an additive-field compatibility note says otherwise. A field marked nullable remains present with a JSON `null` value when the event log does not provide enough information.

`producer.name` and `producer.version` are non-null strings. `producer.name` is `"SparkDoctor"`; `producer.version` identifies the SparkDoctor executable that produced the report. Producer version and schema version are independent: an executable release can change without changing the compatible schema-1 contract.

### Application And Summary

`application` has the following stable fields:

| Field | JSON type | Nullable |
| --- | --- | --- |
| `id` | string | yes |
| `name` | string | yes |
| `startTimeMillis` | integer | yes |
| `endTimeMillis` | integer | yes |
| `durationMillis` | integer | yes |

`durationMillis` is available only when both application start and end timestamps are available.

Every `summary` field is a non-null JSON integer:

- `jobs`, `jobsCompleted`, and `jobsFailed`
- `stages`, `stagesCompleted`, and `stagesFailed`
- `tasks` and `issuesDetected`

`summary.tasks` counts successful logical tasks selected for analysis. For a stage, SparkDoctor selects the highest observed stage-attempt ID and deduplicates successful attempts by logical task index; failed attempts and successful tasks from superseded stage attempts are not added to this count. Successful task events that lack stage/index information are counted by distinct task ID. Failed retry work is represented separately by stage failed-attempt fields and detector evidence when available.

### Stage Analyses

Each object in `stages` has a non-null integer `id`. `name` is a nullable string and `taskCount` is a nullable integer because either can be absent from the event log.

Successful task duration, shuffle, spill, speculation, duplicate-attempt, and worker metrics come from the highest observed attempt for that stage. Stage completion/failure counts and failed-stage details also use that highest attempt rather than event arrival order. When more than one successful task event has the same logical task index in the selected attempt, SparkDoctor retains the first success for task metrics and records later successes in `duplicateSuccessfulTaskAttempts`. Failed-task-attempt counts, durations, and reasons in stage analysis aggregate failed work across all observed attempts for the stage.

The following stage fields are always non-null integers:

- `completedTasks`
- `failedTaskAttempts` and `failedTaskAttemptDurationMillis`
- `speculativeTaskAttempts` and `speculativeTaskAttemptDurationMillis`
- `duplicateSuccessfulTaskAttempts`
- `shuffleReadBytes`, `memoryBytesSpilled`, and `diskBytesSpilled`

Task-duration summary fields and per-task shuffle/spill maximum and percentile fields are nullable integers when no usable samples exist. The corresponding sample arrays, `taskDurationMillis`, `taskShuffleReadBytes`, `taskMemoryBytesSpilled`, and `taskDiskBytesSpilled`, are always present and contain integers. `failedTaskAttemptReasons` is always present and contains strings.

`executorSummaries` and `hostSummaries` are always-present arrays. Each worker summary has a string `id`, integer `taskCount` and `taskDurationMillis`, and numeric `taskShare` and `durationShare`.

### SQL Executions And Failures

Each object in `sqlExecutions` has these public fields:

| Field | JSON type | Nullable |
| --- | --- | --- |
| `id` | integer | no |
| `rootExecutionId` | integer | yes |
| `description` | string | yes |
| `details` | string | yes |
| `startTimeMillis` | integer | yes |
| `endTimeMillis` | integer | yes |
| `durationMillis` | integer | yes |
| `physicalPlanDescription` | string | yes |
| `latestPhysicalPlanDescription` | string | yes |
| `errorMessage` | string | yes |
| `operatorSummaries` | array | no |

Each operator summary contains a non-null string `name` and a non-null integer `count`. Raw Spark plan trees and derived plan internals are deliberately excluded from `analysis.json`: `sparkPlanInfo`, `latestSparkPlanInfo`, `planRoot`, `latestPlanRoot`, and `sqlMetricValues` are not public JSON fields. The physical-plan description strings and operator summaries remain public.

Each `failedJobs` object contains a non-null integer `id` and nullable string `result`. Each `failedStages` object contains a non-null integer `id`, nullable strings `name` and `failureReason`, non-null integer `failedTaskAttempts` and `failedTaskAttemptDurationMillis`, and an always-present string array `failedTaskAttemptReasons`.

### Findings And Recommendations

Each current-emitter `bottlenecks` object contains non-null `instanceId`, `type`, `severity`, and `message` strings, a non-null integer `stageId`, and a non-null `evidence` object. `instanceId` uniquely identifies that finding within one report. Evidence keys and value types are detector-specific. New evidence keys may be added within schema version 1, so consumers should read only the keys they understand and ignore the rest.

Each current-emitter `recommendations` object contains non-null `id`, `severity`, `title`, `description`, `relatedBottleneckType`, and `relatedBottleneckId` strings plus a non-null integer `stageId`. `relatedBottleneckId` references the corresponding `bottlenecks[*].instanceId` in the same report.

`instanceId` and `relatedBottleneckId` were introduced together after the `0.1.4` release. Historical schema-version-1 reports can omit both fields. Consumers should use the IDs when present; when reading older reports, they may fall back to `relatedBottleneckType` plus `stageId`, recognizing that repeated findings with the same type and scope cannot be correlated unambiguously.

`stageId: -1` means the finding or recommendation applies at application or SQL-execution scope rather than to a Spark stage. Recommendation `id` identifies an action category, not a unique finding instance; the same ID can occur more than once in one report. Use `relatedBottleneckId` for within-report correlation instead of matching only by recommendation ID, bottleneck type, or stage ID.

Array order and JSON object-property order are not contractually guaranteed. Consumers should locate records by their documented fields, tolerate multiple records with the same type or recommendation ID, and avoid treating current serialization order as identity or priority. A bottleneck `instanceId` is scoped to one report and is not a stable cross-run identifier.

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
```

Open the generated SVG with the viewer available on your platform.

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
