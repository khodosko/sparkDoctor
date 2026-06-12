# SQL Plan Duplicate Subtree Collection Design

**Goal:** Add an internal duplicate subtree collector for a single `SqlPlanNode` tree using subtree fingerprints, without introducing any detector or user-facing output yet.

**Scope:** This step includes only duplicate subtree collection logic, a compact duplicate subtree summary model, and unit tests. It does not add any `SqlExecution` integration, detector logic, JSON output, markdown output, or DOT output changes.

## Design

- Add a dedicated collector utility at `src/main/java/com/sparkdoctor/analysis/SqlPlanDuplicateSubtreeCollector.java`.
- Add a result model at `src/main/java/com/sparkdoctor/model/DuplicateSqlSubtree.java`.
- Public entry point:
  - `List<DuplicateSqlSubtree> findDuplicates(SqlPlanNode root)`

## Collection Rules

- Traverse every subtree rooted at every node in the input tree.
- Fingerprint each subtree using the existing `SqlPlanSubtreeFingerprinter`.
- Group by fingerprint.
- Return only groups with `count >= 2`.
- Use one representative subtree per fingerprint group to compute structural metadata.
- Sort results by:
  - subtree size descending
  - count descending
  - max depth descending
  - root node name ascending

## Duplicate Result Model

Each `DuplicateSqlSubtree` stores:
- fingerprint
- root node name
- count
- subtree size
- max depth
- operator names
- interesting operators

`operatorNames` contains every operator name present in the representative subtree.

`interestingOperators` is a conservative subset matching operator names containing:
- `Exchange`
- `Join`
- `Aggregate`
- `Sort`
- `Scan`

Set fields are stored as deterministic sorted unmodifiable sets.

## Test Plan

- no duplicates returns an empty list
- one duplicated subtree returns one group
- duplicate count is correct
- subtree size and max depth come from the representative subtree
- operator names are collected deterministically
- interesting operators are collected conservatively
- non-interesting duplicates are still returned
- results are sorted by subtree size desc, count desc, max depth desc, root name asc
- fingerprints with Spark-generated noise still collapse into a single duplicate group

## Out Of Scope

- `SqlExecution` integration
- detector thresholds or severity
- recommendation text
- markdown evidence
- SQL markdown repeated-subtree sections
- DOT graph highlighting
