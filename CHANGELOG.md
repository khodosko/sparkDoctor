# Changelog

All notable changes to SparkDoctor will be documented in this file.

## Unreleased

- Added local Spark event log analysis CLI.
- Added `analysis.json` output.
- Added `recommendations.md` output.
- Added task duration skew detection.
- Added shuffle partition skew detection.
- Added spill pressure detection.
- Added stage-level shuffle read metrics.
- Added per-task shuffle read distribution metrics.
- Added stage-level memory and disk spill metrics.
- Added successful task attempt aggregation to avoid failed/retried task double-counting.
