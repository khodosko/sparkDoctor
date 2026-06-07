# Changelog

All notable changes to SparkDoctor will be documented in this file.

## Unreleased

## 0.1.0 - 2026-06-06

- Added the local `sparkdoctor analyze` CLI.
- Added local Spark event-log parsing for files and directories.
- Added streaming event-log parsing so large logs are not loaded into memory at once.
- Added support for plain, gzip, Zstandard, LZ4, and Snappy event logs.
- Added `analysis.json`, `recommendations.md`, `sql-executions.md`, and SQL plan DOT outputs.
- Added task duration skew, shuffle partition skew, oversized shuffle partition, low shuffle parallelism, spill pressure, memory/disk spill skew, tiny task, retry waste, speculative execution, executor/host imbalance, SQL exchange, failed job, and failed stage detections.
- Added stage hotspots, bottleneck evidence, severity summaries, and actionable parse-failure guidance.
- Added real Spark-generated fixture coverage.
- Added Apache 2.0 license, contribution docs, issue templates, CI, and public roadmap.
