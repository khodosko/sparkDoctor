# SparkDoctor Detections

SparkDoctor parses Spark listener events and builds stage-level metrics from successful task attempts.

Use it as a Spark event log performance analyzer for offline Spark troubleshooting, Spark shuffle skew detection, Spark spill analysis, and Spark task skew diagnostics.

## Parsed Events

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

## Metrics

Current summary and stage metrics include:

- completed task count
- completed and failed job counts
- completed and failed stage counts
- failed job details
- failed stage details and failure reasons
- SQL execution descriptions, timing, physical plan text, and operator counts
- min, max, and average task duration
- median, p95, and p99 task duration
- per-task duration distribution
- failed task attempt count, duration, and reason summaries
- successful speculative task attempt count, duration, and duplicate successful attempt count
- executor and host task distribution summaries
- total shuffle read bytes
- max task shuffle read bytes
- median, p95, and p99 task shuffle read bytes
- per-task shuffle read distribution
- total memory bytes spilled
- total disk bytes spilled
- max task memory bytes spilled
- max task disk bytes spilled
- median, p95, and p99 task memory bytes spilled
- median, p95, and p99 task disk bytes spilled
- per-task memory and disk spill distributions

Failed task attempts do not contribute to successful duration, shuffle, spill, or worker metrics. Their count, duration, and unique reasons are aggregated across all attempts of a stage for retry-waste analysis. Successful metrics, terminal completion state, and failed-stage details come from the highest observed stage attempt ID. Successful task attempts within that stage attempt are deduplicated by task index. When more than one successful attempt exists for an index in the selected stage attempt, SparkDoctor retains the first success while still counting duplicate successes and all successful speculative attempts for that selected attempt.

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
- and a stage with at least 10 shuffle-reading tasks does not qualify for the more specific shuffle partition skew rule

The skew suppression does not apply to stages with 2-9 shuffle-reading tasks because those stages cannot qualify for `shuffle_partition_skew`.

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

### Memory And Disk Spill Skew

Reports `memory_spill_skew` or `disk_spill_skew` when one or a few tasks spill much more than the typical task in the same stage.

For memory spill skew:

- stage has at least 10 completed tasks
- median task memory spill is greater than zero
- max task memory spill is greater than median task memory spill times 5
- max task memory spill is greater than 256 MiB

For disk spill skew:

- stage has at least 10 completed tasks
- median task disk spill is greater than zero
- max task disk spill is greater than median task disk spill times 5
- max task disk spill is greater than 128 MiB

Severity is:

- `medium` for `memory_spill_skew`
- `high` for `disk_spill_skew`

Example:

```text
medianTaskMemoryBytesSpilled = 10 MiB
maxTaskMemoryBytesSpilled = 300 MiB
skewRatio = 30.0
severity = medium
```

### Too Many Tiny Tasks

Reports `too_many_tiny_tasks` when a stage runs many very short tasks where scheduler overhead may be a meaningful part of runtime.

Reports when:

- stage has at least 100 completed tasks
- average task duration is at most 500 ms
- p95 task duration is at most 1000 ms
- and average shuffle read per task is at most 1 MiB, when shuffle read metrics are present

Example:

```text
completedTasks = 100
avgTaskDurationMillis = 200
p95TaskDurationMillis = 200
severity = medium
```

### Retry Waste

Reports `retry_waste` when failed task attempts add meaningful runtime even if the stage eventually has successful task attempts.

Reports when:

- failed task attempts are at least 3
- total failed task attempt duration is at least 30 seconds

Severity is `high` when:

- total failed task attempt duration is at least 5 minutes

Example:

```text
failedTaskAttempts = 3
failedTaskAttemptDurationMillis = 30000
failedTaskAttemptReasons = [ExceptionFailure, ExecutorLostFailure]
severity = medium
```

### Heavy Speculative Execution

Reports `speculation_heavy` when a stage has many successful speculative task attempts relative to the final completed task count.

Reports when:

- stage has at least 10 completed tasks
- successful speculative task attempts are at least 3
- successful speculative task attempts are at least 20% of completed tasks

Severity is `high` when:

- successful speculative task attempts are at least 50% of completed tasks

Example:

```text
completedTasks = 10
speculativeTaskAttempts = 3
speculativeAttemptShare = 0.3
severity = medium
```

### Executor And Host Imbalance

Reports `executor_imbalance` or `host_imbalance` when one executor or host carries most of the successful task work in a stage.

Reports when:

- stage has at least 10 completed tasks
- at least 2 executors or hosts are observed
- one executor or host accounts for at least 75% of successful task duration
- that same executor or host accounts for at least 50% of successful tasks

Example:

```text
executorId = executor-1
workerTaskShare = 0.8
workerDurationShare = 0.8
severity = medium
```

### SQL Plans With Many Exchanges

Reports `sql_many_exchanges` when a Spark SQL physical plan contains many `Exchange` operators.

Reports when:

- a SQL execution has at least 4 `Exchange` operators in its structured physical plan

Example:

```text
sqlExecutionId = 9
exchangeCount = 4
severity = medium
```

### Repeated SQL Physical Plan Subtrees

Reports `duplicate_sql_subtree` when a Spark SQL physical plan contains repeated subtrees after conservative normalization.

SparkDoctor builds a tree from Spark's structured `SparkPlanInfo`, fingerprints every subtree, and groups duplicate fingerprints. The fingerprint removes obvious Spark-generated identity noise such as expression IDs, plan IDs, codegen IDs, and query-stage numbers while preserving semantic values such as partition counts, range bounds, and literal values.

Reports when:

- a SQL execution has repeated physical plan subtrees
- a duplicate group appears at least 2 times
- the duplicated subtree has at least 3 nodes
- the duplicated subtree contains an interesting operator such as `Exchange`, `Join`, `Aggregate`, `Sort`, or `Scan`

Severity is currently `medium`.

Example:

```text
sqlExecutionId = 12
duplicateGroups = 1
topDuplicateRoot = Exchange
topDuplicateCount = 2
topDuplicateSubtreeSize = 3
topDuplicateInterestingOperators = [Exchange, HashAggregate]
severity = medium
```

This is a cautious signal. Repeated physical plan subtrees can indicate duplicated work, missed reuse, or a caching/materialization opportunity, but Spark event logs contain physical plan evidence rather than the full analyzer and optimizer context.

### Possible Missed SQL Exchange Reuse

Reports `possible_missed_exchange_reuse` when a Spark SQL physical plan contains repeated exchange-like physical plan subtrees.

This is a more specific version of repeated subtree analysis. It focuses on duplicate subtree groups rooted at `Exchange` operators, because repeated exchange-like structures may indicate missed exchange reuse or another repeated shuffle-heavy pattern.

Reports when:

- a SQL execution has repeated physical plan subtrees
- a duplicate group appears at least 2 times
- the duplicated subtree has at least 3 nodes
- the duplicated subtree is rooted at `Exchange`

SparkDoctor does not treat duplicate `ReusedExchange` subtrees as possible missed reuse, because the physical plan already shows reuse.

Severity is currently `medium`.

Example:

```text
sqlExecutionId = 12
duplicateExchangeGroups = 1
topDuplicateRoot = Exchange
topDuplicateCount = 2
topDuplicateSubtreeSize = 3
topDuplicateInterestingOperators = [Exchange, HashAggregate]
confidence = low
confidenceReason = physical-plan-only signal
validationRequired = Validate in Spark UI and query code before making optimizer conclusions.
severity = medium
```

This finding does not prove Spark failed to reuse an exchange. Spark event logs contain the physical plan, so analyzer and optimizer context may be missing. Validate the Spark UI and query code before making caching, query-shape, or optimizer conclusions.

### Failed Jobs And Stages

Reports `failed_job` or `failed_stage` when Spark listener completion events show a failed job or stage.

Severity is `high` because a failed job or stage usually means the Spark application did not finish the intended work or paid retry/recovery cost.

For failed stages, SparkDoctor also includes failed task attempt count, failed attempt duration, and failed task reasons recorded for the selected highest stage attempt.

Example:

```text
jobsFailed = 1
stagesFailed = 1
failureReason = Fetch failed
failedTaskAttempts = 2
failedTaskAttemptReasons = [FetchFailed, ExecutorLostFailure]
```
