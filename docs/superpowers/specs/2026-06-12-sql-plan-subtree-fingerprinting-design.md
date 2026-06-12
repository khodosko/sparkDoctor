# SQL Plan Subtree Fingerprinting Design

**Goal:** Add a deterministic, human-readable SQL plan subtree fingerprint primitive that can support later duplicate-subtree analysis without introducing any user-facing detector or report changes yet.

**Scope:** This step includes only the fingerprinting primitive, a small fingerprint value object, conservative normalization logic, and unit tests. It does not change parser behavior, detectors, JSON output, markdown output, or DOT output.

## Design

- Add a dedicated fingerprinter utility at `src/main/java/com/sparkdoctor/analysis/SqlPlanSubtreeFingerprinter.java`.
- Add a small immutable value object at `src/main/java/com/sparkdoctor/model/SqlPlanSubtreeFingerprint.java`.
- Public entry point:
  - `SqlPlanSubtreeFingerprint fingerprint(SqlPlanNode node)`
- Package-private helper:
  - `static String normalize(String value)`

## Fingerprint Rules

- Fingerprints are deterministic, readable canonical strings.
- Fingerprints include:
  - normalized `nodeName`
  - normalized `simpleString`
  - ordered child fingerprints
- Fingerprints do not include runtime metric values.
- Child order matters.
- Subtree shape matters.
- Null or blank `simpleString` values still produce valid fingerprints.

## Normalization Rules

Normalize Spark-generated identity noise, but preserve semantic numbers.

Normalize:
- attribute IDs such as `#12`, `#12L`, `#123`
- `plan_id=37`
- `codegen id : 2` and `codegen id: 2`
- `WholeStageCodegen (3)` to `WholeStageCodegen`
- `ShuffleQueryStage 4` and `ResultQueryStage 2` to stage-name-only forms
- obvious Spark-generated expression ID suffixes in names and expressions

Do not normalize:
- partition counts such as `hashpartitioning(key, 200)`
- range bounds such as `Range (0, 1000, step=1)`
- limits
- literal filter values
- other numbers that plausibly affect execution semantics

## Test Plan

- Direct normalization tests for each known noise pattern
- Direct normalization tests proving semantic numbers remain distinct
- Subtree equivalence tests where only Spark-generated IDs differ
- Subtree inequivalence tests where structure or semantic text differs
- One or two exact canonical-string tests for representative trees
- One null/blank `simpleString` case

## Out Of Scope

- Duplicate subtree collection
- Duplicate subtree detector
- Recommendation text
- Markdown evidence
- SQL markdown repeated-subtree sections
- DOT graph highlighting
