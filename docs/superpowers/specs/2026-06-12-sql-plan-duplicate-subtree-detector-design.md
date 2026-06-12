# SQL Plan Duplicate Subtree Detector Design

**Goal:** Add a conservative aggregated SQL duplicate-subtree detector that evaluates repeated physical plan fragments per SQL execution and emits one cautious bottleneck plus recommendation per affected execution.

**Scope:** This step includes detector logic, recommendation wiring, parser integration, and tests. It does not add markdown detail sections, DOT highlighting, or execution-level duplicate-group reporting yet.

## Design

- Add a detector at `src/main/java/com/sparkdoctor/analysis/SqlPlanDuplicateSubtreeDetector.java`.
- Run it against `SqlExecution.latestPlanRoot()` only.
- Reuse `SqlPlanDuplicateSubtreeCollector`.
- Emit at most one bottleneck per `SqlExecution`.

## Detection Rules

For each SQL execution:
- skip if `latestPlanRoot()` is null
- collect duplicate subtrees from the root
- filter duplicates to qualifying groups:
  - `count >= 2`
  - `subtreeSize >= 3`
  - `interestingOperators` is not empty
- if no qualifying groups remain, emit nothing
- otherwise use the first qualifying group, based on the collector’s sort order, as the top duplicate group

## Bottleneck

- type: `duplicate_sql_subtree`
- severity: `medium`
- stageId: `-1`
- message: cautious wording that repeated physical plan subtrees were found for the SQL execution

Evidence includes:
- `sqlExecutionId`
- `description`
- `duplicateGroups`
- `topDuplicateRoot`
- `topDuplicateCount`
- `topDuplicateSubtreeSize`
- `topDuplicateMaxDepth`
- `topDuplicateInterestingOperators`

Do not include the full fingerprint yet.

## Recommendation

- id: `investigate-duplicate-sql-subtrees`
- title: `Investigate repeated SQL plan subtrees`
- keep wording explicitly cautious
- say repeated physical plan fragments may indicate duplicated work, missing reuse, or an opportunity to cache/materialize an intermediate result
- say event logs expose physical plans only, so optimizer/analyzer context may be missing
- instruct the user to validate in Spark UI or query code before changing logic

## Test Plan

- detector skips executions without `latestPlanRoot`
- detector ignores duplicates that are too small
- detector ignores duplicate groups with no interesting operators
- detector emits one bottleneck for one execution with qualifying duplicates
- detector aggregates multiple qualifying groups into one bottleneck using the top sorted group
- recommendation engine emits the new recommendation for `duplicate_sql_subtree`
- parser integration surfaces the new bottleneck and recommendation when SQL execution data qualifies

## Out Of Scope

- `sql-executions.md` repeated subtree sections
- markdown evidence expansions beyond bottleneck/recommendation output
- DOT graph highlighting
- cross-query duplicate detection
