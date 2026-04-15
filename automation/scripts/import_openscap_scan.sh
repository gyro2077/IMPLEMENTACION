#!/usr/bin/env bash
set -euo pipefail

# Fase 3B: importa a evidence-server el resumen parseado de un scan OpenSCAP.
# Uso:
#   ./automation/scripts/import_openscap_scan.sh <scan_dir> [api_base_url]
# Ejemplo:
#   ./automation/scripts/import_openscap_scan.sh fixtures/openscap/target-cachyos/20260414T235914Z

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "${SCRIPT_DIR}/../.." && pwd)

SCAN_DIR=${1:-}
API_BASE_URL=${2:-http://localhost:8082}

if [[ -z "${SCAN_DIR}" ]]; then
  echo "Uso: $0 <scan_dir> [api_base_url]"
  exit 1
fi

ABS_SCAN_DIR="${REPO_ROOT}/${SCAN_DIR}"
if [[ ! -d "${ABS_SCAN_DIR}" ]]; then
  ABS_SCAN_DIR="${SCAN_DIR}"
fi

if [[ ! -d "${ABS_SCAN_DIR}" ]]; then
  echo "No existe scan_dir: ${SCAN_DIR}"
  exit 1
fi

ARF_PATH="${ABS_SCAN_DIR}/scan-results.arf.xml"
HTML_PATH="${ABS_SCAN_DIR}/scan-report.html"
SUMMARY_PATH="${ABS_SCAN_DIR}/scan-summary.json"
META_PATH="${ABS_SCAN_DIR}/run-metadata.json"

if [[ ! -f "${ARF_PATH}" || ! -f "${HTML_PATH}" ]]; then
  echo "Faltan artefactos. Se requiere ARF y HTML en ${ABS_SCAN_DIR}"
  exit 1
fi

HOST_NAME="target-local"
if [[ -f "${META_PATH}" ]]; then
  HOST_NAME=$(python3 - <<PY
import json
from pathlib import Path
p = Path("${META_PATH}")
try:
    print(json.loads(p.read_text(encoding="utf-8")).get("host", "target-local"))
except Exception:
    print("target-local")
PY
)
fi

python3 "${SCRIPT_DIR}/parse_openscap.py" \
  --arf "${ARF_PATH}" \
  --html "${HTML_PATH}" \
  --host "${HOST_NAME}" \
  --output "${SUMMARY_PATH}" >/dev/null

echo "Importando resumen en ${API_BASE_URL}/api/compliance/scans/import"
curl -s -X POST "${API_BASE_URL}/api/compliance/scans/import" \
  -H "Content-Type: application/json" \
  --data-binary @"${SUMMARY_PATH}"

echo
