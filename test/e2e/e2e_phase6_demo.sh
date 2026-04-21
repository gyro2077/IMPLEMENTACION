#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "${SCRIPT_DIR}/../.." && pwd)

MAX_SECONDS=${1:-420}

echo "[e2e] Reset demo environment"
"${REPO_ROOT}/automation/scripts/reset_demo.sh"

echo "[e2e] Run full demo flow"
"${REPO_ROOT}/automation/scripts/demo_run.sh" --max-seconds "${MAX_SECONDS}"

SUMMARY_JSON="${REPO_ROOT}/reports/generated/demo-last-run.json"
if [[ ! -f "${SUMMARY_JSON}" ]]; then
  echo "ERROR: no existe ${SUMMARY_JSON}"
  exit 1
fi

echo "[e2e] Validate measured metrics"
python3 - <<PY
import json
from pathlib import Path

summary = json.loads(Path("${SUMMARY_JSON}").read_text(encoding="utf-8"))

assert summary.get("target_under_7min") == "pass", "demo fuera de objetivo de tiempo"
assert int(summary.get("manual_commands", 99)) <= 2, "demasiados comandos manuales"
assert int(summary.get("failures", 99)) == 0, "hubo fallos en demo"
pdf_path = Path(summary.get("pdf_path", ""))
assert pdf_path.exists(), "no existe PDF"
assert pdf_path.stat().st_size > 0, "PDF vacio"
PY

echo "[e2e] PASS"
