# SQL Executions Repeated Subtrees Design

**Goal:** Add a `Repeated Subtrees` section to `sql-executions.md` so duplicate SQL plan fragments are inspectable per execution without changing detector behavior or `analysis.json`.

**Scope:** This step includes only `SqlExecutionsMarkdownWriter` presentation logic and markdown-writer tests. It does not change detector logic, parser behavior, recommendation text, `analysis.json`, or DOT output.

## Design

- `SqlExecutionsMarkdownWriter` computes duplicate subtrees directly from `SqlExecution.latestPlanRoot()`.
- Use `SqlPlanDuplicateSubtreeCollector` inside the writer.
- Render a `### Repeated Subtrees` section for a SQL execution only when duplicates are present.
- Omit the section entirely when no duplicates are found.

## Output Format

Render one compact bullet per duplicate group in collector sort order:

- `Root: <root>; count=<count>; subtreeSize=<size>; maxDepth=<depth>; contains=<ops>; interesting=<ops>`

Rules:
- `contains` lists all operator names in deterministic order
- `interesting` lists interesting operators in deterministic order
- omit the `interesting=` segment when the interesting-operator set is empty

## Placement

- Within each SQL execution section
- After `### Operator Summary`
- Before `### Details` and physical plan sections

## Test Plan

- writes repeated-subtree section when `latestPlanRoot()` has duplicates
- omits repeated-subtree section when there are no duplicates
- preserves collector sort order in rendered bullets
- omits `interesting=` when the duplicate group has no interesting operators

## Out Of Scope

- detector changes
- parser integration changes
- `analysis.json` changes
- `recommendations.md` changes
- DOT highlighting
