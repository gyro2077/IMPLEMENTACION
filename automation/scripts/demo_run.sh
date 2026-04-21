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
MAX_SECONDS=420
WITH_BUILD=0
SKIP_RESET=0

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
    --max-seconds)
      MAX_SECONDS="$2"
      shift 2
      ;;
    --with-build)
      WITH_BUILD=1
      shift
      ;;
    --skip-reset)
      SKIP_RESET=1
      shift
      ;;
    *)
      echo "Parametro no soportado: $1"
      exit 1
      ;;
  esac
done

log() {
  printf '[demo] %s\n' "$*"
}

wait_http_ok() {
  local url="$1"
  local name="$2"
  local retries=40
  while (( retries > 0 )); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      log "$name listo"
      return 0
    fi
    retries=$((retries - 1))
    sleep 2
  done
  log "ERROR: $name no responde en $url"
  return 1
}

ensure_stack() {
  if [[ "$WITH_BUILD" -eq 1 ]]; then
    log "Levantando compose con build"
    docker compose -f "${REPO_ROOT}/compose/docker-compose.yml" up -d --build
  fi

  wait_http_ok "${TARGET_BASE_URL}/actuator/health" "target-app"
  wait_http_ok "${API_BASE_URL}/actuator/health" "evidence-server"
}

run_step() {
  local step_name="$1"
  shift
  local start end elapsed
  start=$(date +%s)
  log "Inicio: ${step_name}"
  "$@"
  end=$(date +%s)
  elapsed=$((end - start))
  STEP_REPORT+="${step_name}=${elapsed}s\n"
  log "Fin: ${step_name} (${elapsed}s)"
}

REPORTS_DIR="${REPO_ROOT}/reports/generated"
mkdir -p "${REPORTS_DIR}"
RUN_TS=$(date -u +%Y%m%dT%H%M%SZ)
SUMMARY_JSON="${REPORTS_DIR}/demo-run-${RUN_TS}.json"
SUMMARY_MD="${REPORTS_DIR}/demo-run-${RUN_TS}.md"
LAST_JSON="${REPORTS_DIR}/demo-last-run.json"
LAST_MD="${REPORTS_DIR}/demo-last-run.md"
PDF_PATH="${REPORTS_DIR}/demo-report-${RUN_TS}.pdf"

TOTAL_START=$(date +%s)
STEP_REPORT=""

run_step "stack-check" ensure_stack

if [[ "$SKIP_RESET" -eq 0 ]]; then
  run_step "reset-demo" "${SCRIPT_DIR}/reset_demo.sh" --host "${HOST_NAME}"
fi

run_step "seed-demo-data" "${SCRIPT_DIR}/seed_demo_data.sh" --host "${HOST_NAME}" --api-base "${API_BASE_URL}" --target-base "${TARGET_BASE_URL}" --no-reset
run_step "backup-full" "${SCRIPT_DIR}/backup_db.sh" "${HOST_NAME}"
run_step "restore-isolated" "${SCRIPT_DIR}/restore_db.sh"
run_step "smoke-restore" "${SCRIPT_DIR}/smoke_test_restore.sh"

run_step "generate-report" bash -lc '
  set -euo pipefail
  RESP=$(curl -sS -w "\nHTTP_STATUS:%{http_code}\n" -X POST "'"${API_BASE_URL}"'/api/reports/evidence/'"${HOST_NAME}"'" -H "Content-Type: application/json")
  BODY="${RESP%HTTP_STATUS:*}"
  CODE="${RESP##*HTTP_STATUS:}"
  CODE="$(echo "${CODE}" | tr -d "[:space:]")"
  if [[ "${CODE}" != "201" ]]; then
    echo "ERROR: fallo POST reporte (HTTP ${CODE})"
    echo "${BODY}"
    exit 1
  fi
  REPORT_ID=$(python3 - <<PY
import json
import sys
body = """${BODY}""".strip()
print(json.loads(body).get("id", ""))
PY
)
  if [[ -z "${REPORT_ID}" ]]; then
    echo "ERROR: no se pudo extraer report_id"
    exit 1
  fi

  for _ in $(seq 1 25); do
    STATUS_JSON=$(curl -fsS "'"${API_BASE_URL}"'/api/reports/${REPORT_ID}")
    STATUS=$(python3 - <<PY
import json
print(json.loads("""${STATUS_JSON}""").get("status", ""))
PY
)
    if [[ "${STATUS}" == "COMPLETED" ]]; then
      break
    fi
    if [[ "${STATUS}" == "FAILED" ]]; then
      echo "ERROR: reporte en FAILED"
      echo "${STATUS_JSON}"
      exit 1
    fi
    sleep 1
  done

  curl -fsS -o "'"${PDF_PATH}"'" "'"${API_BASE_URL}"'/api/reports/${REPORT_ID}/download"
  if [[ ! -s "'"${PDF_PATH}"'" ]]; then
    echo "ERROR: PDF vacio"
    exit 1
  fi
  echo "${REPORT_ID}" > "'"${REPORTS_DIR}"'/demo-last-report-id.txt"
'

TOTAL_END=$(date +%s)
TOTAL_SECONDS=$((TOTAL_END - TOTAL_START))

OVERVIEW_JSON=$(curl -fsS "${API_BASE_URL}/api/evidence/overview")
HOSTS_TOTAL=$(python3 - <<PY
import json
obj = json.loads("""${OVERVIEW_JSON}""")
print(obj.get("totals", {}).get("hosts", 0))
PY
)

if [[ "${TOTAL_SECONDS}" -le "${MAX_SECONDS}" ]]; then
  TARGET_STATUS="pass"
else
  TARGET_STATUS="fail"
fi

cat > "${SUMMARY_JSON}" <<EOF
{
  "run_timestamp_utc": "${RUN_TS}",
  "host": "${HOST_NAME}",
  "total_seconds": ${TOTAL_SECONDS},
  "max_seconds": ${MAX_SECONDS},
  "target_under_7min": "${TARGET_STATUS}",
  "manual_commands": 1,
  "failures": 0,
  "recovery_mode": "reset_demo.sh + demo_run.sh",
  "pdf_path": "${PDF_PATH}",
  "hosts_in_overview": ${HOSTS_TOTAL},
  "step_durations": "$(echo -e "${STEP_REPORT}" | tr '\n' ';' | sed 's/;$/ /')"
}
EOF

cat > "${SUMMARY_MD}" <<EOF
# Demo run ${RUN_TS}

- Host demo: ${HOST_NAME}
- Tiempo total: ${TOTAL_SECONDS}s
- Objetivo (< ${MAX_SECONDS}s): ${TARGET_STATUS}
- Comandos manuales: 1
- Fallos: 0
- PDF generado: ${PDF_PATH}

## Duraciones por paso

$(echo -e "${STEP_REPORT}" | sed 's/^/- /')

## Resultado

- Demo ejecutable de punta a punta: OK
- Evidencia generada automaticamente: OK
- Flujo listo para audiencia no tecnica: OK
EOF

cp "${SUMMARY_JSON}" "${LAST_JSON}"
cp "${SUMMARY_MD}" "${LAST_MD}"

log "Resumen JSON: ${SUMMARY_JSON}"
log "Resumen MD: ${SUMMARY_MD}"
log "Resumen ultimo run: ${LAST_JSON}"

if [[ "${TARGET_STATUS}" != "pass" ]]; then
  log "ERROR: demo supero el tiempo objetivo (${TOTAL_SECONDS}s > ${MAX_SECONDS}s)"
  exit 2
fi

log "Demo completada en ${TOTAL_SECONDS}s"
