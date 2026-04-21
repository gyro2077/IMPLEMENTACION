#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "${SCRIPT_DIR}/../.." && pwd)
ENV_FILE="${REPO_ROOT}/compose/.env"

if [[ -f "${ENV_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
  set +a
fi

HOST_NAME="target-cachyos"
API_BASE_URL="http://localhost:${EVIDENCE_SERVER_PORT:-8082}"
TARGET_BASE_URL="http://localhost:${TARGET_APP_PORT:-8081}"
RESET_METADATA=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)
      HOST_NAME="$2"
      shift 2
      ;;
    --api-base)
      API_BASE_URL="$2"
      shift 2
      ;;
    --target-base)
      TARGET_BASE_URL="$2"
      shift 2
      ;;
    --no-reset)
      RESET_METADATA=0
      shift
      ;;
    *)
      echo "Parametro no soportado: $1"
      exit 1
      ;;
  esac
done

META_CONTAINER="flisol-postgres-metadata"
DATASET_TEMPLATE="${REPO_ROOT}/fixtures/demo/compliance/scan-summary-target-cachyos.json"

if [[ ! -f "${DATASET_TEMPLATE}" ]]; then
  echo "No existe dataset template: ${DATASET_TEMPLATE}"
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -q "^${META_CONTAINER}$"; then
  echo "No esta activo ${META_CONTAINER}. Levanta docker compose primero."
  exit 1
fi

if [[ "${RESET_METADATA}" -eq 1 ]]; then
  echo "[seed] Limpiando metadata para dataset reproducible"
  docker exec -i "${META_CONTAINER}" psql -v ON_ERROR_STOP=1 -U "${META_DB_USER}" -d "${META_DB_NAME}" <<'SQL'
DO $$
BEGIN
  IF to_regclass('public.compliance_rule_result') IS NOT NULL THEN
    TRUNCATE TABLE compliance_rule_result CASCADE;
  END IF;
  IF to_regclass('public.compliance_scan') IS NOT NULL THEN
    TRUNCATE TABLE compliance_scan CASCADE;
  END IF;
  IF to_regclass('public.backup_run') IS NOT NULL THEN
    TRUNCATE TABLE backup_run CASCADE;
  END IF;
  IF to_regclass('public.restore_run') IS NOT NULL THEN
    TRUNCATE TABLE restore_run CASCADE;
  END IF;
  IF to_regclass('public.report_job') IS NOT NULL THEN
    TRUNCATE TABLE report_job CASCADE;
  END IF;
END $$;
SQL
fi

echo "[seed] Preparando payload de compliance"
TMP_JSON=$(mktemp)
python3 - <<PY
import json
from datetime import datetime, timedelta, timezone
from pathlib import Path

src = Path("${DATASET_TEMPLATE}")
out = Path("${TMP_JSON}")
data = json.loads(src.read_text(encoding="utf-8"))

now = datetime.now(timezone.utc)
started = now - timedelta(minutes=2)

base = Path("${REPO_ROOT}").resolve()
data["host"] = "${HOST_NAME}"
data["started_at"] = started.isoformat().replace("+00:00", "Z")
data["finished_at"] = now.isoformat().replace("+00:00", "Z")
data["generated_at"] = now.isoformat().replace("+00:00", "Z")
data["arf_path"] = str((base / "fixtures" / "demo" / "compliance" / "scan-results.arf.xml"))
data["html_report_path"] = str((base / "fixtures" / "demo" / "compliance" / "scan-report.html"))
out.write_text(json.dumps(data), encoding="utf-8")
PY

echo "[seed] Importando compliance en evidence-server"
curl -fsS -X POST "${API_BASE_URL}/api/compliance/scans/import" \
  -H "Content-Type: application/json" \
  --data-binary @"${TMP_JSON}" >/dev/null

rm -f "${TMP_JSON}"

echo "[seed] Generando trafico minimo para observabilidad"
for _ in 1 2 3 4 5; do
  curl -fsS "${TARGET_BASE_URL}/api/ping" >/dev/null || true
  sleep 0.2
done

echo "[seed] Dataset demo listo para host ${HOST_NAME}"
echo "[seed] API base: ${API_BASE_URL}"
