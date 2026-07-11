#!/usr/bin/env bash
set -euo pipefail

if ! command -v spark-submit >/dev/null 2>&1; then
  echo "spark-submit is required to generate the real Spark event log fixture." >&2
  echo "Install Apache Spark or run this script in an environment where spark-submit is on PATH." >&2
  exit 1
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work_dir="$(mktemp -d)"
event_log_dir="$work_dir/spark-events"
target="$repo_root/src/test/resources/fixtures/real-spark-eventlog.json"
sanitized_target=""

cleanup() {
  rm -rf "$work_dir"
  if [[ -n "$sanitized_target" ]]; then
    rm -f "$sanitized_target"
  fi
}

trap cleanup EXIT

mkdir -p "$event_log_dir"

spark-submit \
  --master local[2] \
  --conf spark.eventLog.enabled=true \
  --conf "spark.eventLog.dir=file:$event_log_dir" \
  "$repo_root/scripts/generate-real-spark-eventlog-fixture.py"

event_log=""
event_log_size=0

file_size() {
  if stat -f%z "$1" >/dev/null 2>&1; then
    stat -f%z "$1"
  else
    stat -c%s "$1"
  fi
}

while IFS= read -r candidate; do
  candidate_size="$(file_size "$candidate")"
  if [[ "$candidate_size" -gt "$event_log_size" ]]; then
    event_log="$candidate"
    event_log_size="$candidate_size"
  fi
done < <(find "$event_log_dir" -type f ! -name '*.inprogress' -size +0c)

if [[ -z "$event_log" ]]; then
  echo "No non-empty completed Spark event log was generated in $event_log_dir" >&2
  find "$event_log_dir" -maxdepth 4 -type f -exec ls -lh {} \; >&2
  exit 1
fi

raw_event_log="$work_dir/raw-eventlog.json"
if file "$event_log" | grep -qi "Zstandard"; then
  if ! command -v zstd >/dev/null 2>&1; then
    echo "Spark generated a Zstandard-compressed event log, but zstd is not on PATH." >&2
    echo "Install zstd or configure Spark to write uncompressed event logs." >&2
    exit 1
  fi
  zstd -dc "$event_log" > "$raw_event_log"
else
  cp "$event_log" "$raw_event_log"
fi

echo "Copied Spark event log source: $event_log"
sanitized_target="$(mktemp "${target}.tmp.XXXXXX")"
python3 "$repo_root/scripts/sanitize_real_spark_eventlog_fixture.py" \
  --input "$raw_event_log" \
  --output "$sanitized_target" \
  --repo-root "$repo_root" \
  --home-directory "${HOME:-}" \
  --username "${USER:-}"
mv "$sanitized_target" "$target"
sanitized_target=""
echo "Sanitized machine-specific paths and IDs in $target"
echo "Wrote $target"
