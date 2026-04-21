#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "${SCRIPT_DIR}/../.." && pwd)

HOST_NAME=${1:-target-cachyos}
API_BASE_URL=${2:-http://localhost:8082}
TARGET_BASE_URL=${3:-http://localhost:8081}

echo "[integration] Seed dataset"
"${REPO_ROOT}/automation/scripts/seed_demo_data.sh" --host "${HOST_NAME}" --api-base "${API_BASE_URL}" --target-base "${TARGET_BASE_URL}"

echo "[integration] Check APIs"
HOSTS_JSON=$(curl -fsS "${API_BASE_URL}/api/hosts")
SCANS_JSON=$(curl -fsS "${API_BASE_URL}/api/compliance/scans")
OVERVIEW_JSON=$(curl -fsS "${API_BASE_URL}/api/evidence/overview")
PING_JSON=$(curl -fsS "${TARGET_BASE_URL}/api/ping")

python3 - <<PY
import json

hosts = json.loads('''${HOSTS_JSON}''')
scans = json.loads('''${SCANS_JSON}''')
overview = json.loads('''${OVERVIEW_JSON}''')
ping = json.loads('''${PING_JSON}''')

assert isinstance(hosts, list) and len(hosts) >= 1, "hosts vacio"
assert any(h.get("host") == "${HOST_NAME}" for h in hosts), "host demo no encontrado"
assert isinstance(scans, list) and len(scans) >= 1, "scans vacio"
assert overview.get("totals", {}).get("hosts", 0) >= 1, "overview sin hosts"
assert ping.get("status") == "ok", "target-app ping no OK"
PY

echo "[integration] Generate one report and verify status"
RESP=$(curl -sS -w '\nHTTP_STATUS:%{http_code}\n' -X POST "${API_BASE_URL}/api/reports/evidence/${HOST_NAME}" -H 'Content-Type: application/json')
BODY="${RESP%HTTP_STATUS:*}"
CODE="${RESP##*HTTP_STATUS:}"
CODE="$(echo "${CODE}" | tr -d '[:space:]')"

if [[ "${CODE}" != "201" ]]; then
  echo "ERROR: POST report fallo con HTTP ${CODE}"
  echo "${BODY}"
  exit 1
fi

REPORT_ID=$(python3 - <<PY
import json
print(json.loads('''${BODY}''').get("id", ""))
PY
)

[[ -n "${REPORT_ID}" ]] || { echo "ERROR: report id vacio"; exit 1; }

STATUS_JSON=$(curl -fsS "${API_BASE_URL}/api/reports/${REPORT_ID}")
STATUS=$(python3 - <<PY
import json
print(json.loads('''${STATUS_JSON}''').get("status", ""))
PY
)
[[ "${STATUS}" == "COMPLETED" ]] || { echo "ERROR: status esperado COMPLETED, actual ${STATUS}"; exit 1; }

echo "[integration] PASS"
