---
name: Bug report
about: Report incorrect analysis, parser behavior, or CLI output
title: "[Bug]: "
labels: bug
assignees: ""
---

## What happened?

Describe the issue.

## What did you expect?

Describe what SparkDoctor should have reported or generated.

## Command

```bash
sparkdoctor analyze path/to/eventlog --out ./sparkdoctor-report
```

## Environment

- SparkDoctor version or commit:
- Spark version:
- Environment: local Spark / EMR / Databricks / Kubernetes / YARN / other
- Java version:
- Operating system:

## Output

Please include relevant output from:

```bash
cat sparkdoctor-report/analysis.json
cat sparkdoctor-report/recommendations.md
```

## Event Log

If possible, attach a small sanitized Spark event log that reproduces the issue.

Do not attach secrets, credentials, customer data, or proprietary data.
