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

if file "$event_log" | grep -qi "Zstandard"; then
  if ! command -v zstd >/dev/null 2>&1; then
    echo "Spark generated a Zstandard-compressed event log, but zstd is not on PATH." >&2
    echo "Install zstd or configure Spark to write uncompressed event logs." >&2
    exit 1
  fi
  zstd -dc "$event_log" > "$target"
else
  cp "$event_log" "$target"
fi

echo "Copied Spark event log source: $event_log"
python3 - "$target" "$repo_root" <<'PY'
import re
import sys
from pathlib import Path

target = Path(sys.argv[1])
repo_root = sys.argv[2]
content = target.read_text()
content = content.replace(repo_root, "/tmp/sparkdoctor")
content = re.sub(r"/Users/[^/\"',;:} ]+", "/tmp/sparkdoctor-user", content)
content = re.sub(r"/var/folders/[^\"',;:} ]+", "/tmp/sparkdoctor-temp", content)
content = re.sub(r"local-\d+", "local-sparkdoctor-fixture", content)
content = re.sub(r'"User":"[^"]+"', '"User":"sparkdoctor-user"', content)
content = re.sub(r"(?<![\w.-])10\.\d{1,3}\.\d{1,3}\.\d{1,3}(?![\w.-])", "127.0.0.1", content)
content = re.sub(r"file:/var/folders/[^\",}]+", "file:/tmp/sparkdoctor-events", content)
target.write_text(content)
PY
echo "Sanitized machine-specific paths and IDs in $target"
echo "Wrote $target"
