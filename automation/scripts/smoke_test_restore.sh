#!/usr/bin/env bash
set -euo pipefail

# Fase 4: smoke tests post-restore en entorno aislado.
# Uso:
#   ./automation/scripts/smoke_test_restore.sh

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "${SCRIPT_DIR}/../.." && pwd)
ENV_FILE="${REPO_ROOT}/compose/.env"

if [[ -f "${ENV_FILE}" ]]; then
	set -a
	# shellcheck disable=SC1090
	source "${ENV_FILE}"
	set +a
fi

RESTORE_CONTAINER="flisol-postgres-restore"
META_CONTAINER="flisol-postgres-metadata"
ART_BASE="${REPO_ROOT}/fixtures/pgbackrest"
BACKUP_META_FILE="${ART_BASE}/latest-backup.json"
RESTORE_META_FILE="${ART_BASE}/latest-restore.json"

if [[ ! -f "${BACKUP_META_FILE}" || ! -f "${RESTORE_META_FILE}" ]]; then
	echo "Faltan metadatos latest-backup.json o latest-restore.json"
	exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -q "^${RESTORE_CONTAINER}$"; then
	echo "No esta activo ${RESTORE_CONTAINER}. Ejecuta restore_db.sh primero."
	exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -q "^${META_CONTAINER}$"; then
	echo "No esta activo ${META_CONTAINER}. Levanta docker compose primero."
	exit 1
fi

HOST_NAME=$(python3 - <<PY
import json
from pathlib import Path
print(json.loads(Path("${BACKUP_META_FILE}").read_text(encoding="utf-8")).get("host", "target-local"))
PY
)

BACKUP_LABEL=$(python3 - <<PY
import json
from pathlib import Path
print(json.loads(Path("${BACKUP_META_FILE}").read_text(encoding="utf-8")).get("backup_label", ""))
PY
)

EXPECTED_ROWS=$(python3 - <<PY
import json
from pathlib import Path
print(int(json.loads(Path("${BACKUP_META_FILE}").read_text(encoding="utf-8")).get("expected_rows", 0)))
PY
)

EXPECTED_SUM=$(python3 - <<PY
import json
from pathlib import Path
print(str(json.loads(Path("${BACKUP_META_FILE}").read_text(encoding="utf-8")).get("expected_sum", "0")))
PY
)

RUN_TS=$(date -u +%Y%m%dT%H%M%SZ)
RUN_DIR="${ART_BASE}/${HOST_NAME}/smoke-${RUN_TS}"
mkdir -p "${RUN_DIR}"

TESTS_TOTAL=4
TESTS_PASSED=0

# 1) Conexion DB restaurada.
CONN_RESULT=$(docker exec -u postgres "${RESTORE_CONTAINER}" psql -tAX -U "${APP_DB_USER}" -d "${APP_DB_NAME}" -c "SELECT 1;" || true)
if [[ "${CONN_RESULT}" == "1" ]]; then
	DB_CONNECT_STATUS="pass"
	TESTS_PASSED=$((TESTS_PASSED + 1))
else
	DB_CONNECT_STATUS="fail"
fi

# 2) Conteo de filas.
ROWS_RESTORED=$(docker exec -u postgres "${RESTORE_CONTAINER}" psql -tAX -U "${APP_DB_USER}" -d "${APP_DB_NAME}" -c "SELECT COUNT(*) FROM demo_orders;" || echo "0")
if [[ "${ROWS_RESTORED}" == "${EXPECTED_ROWS}" ]]; then
	ROW_COUNT_STATUS="pass"
	TESTS_PASSED=$((TESTS_PASSED + 1))
else
	ROW_COUNT_STATUS="fail"
fi

# 3) Query critica de consistencia (suma de montos).
SUM_RESTORED=$(docker exec -u postgres "${RESTORE_CONTAINER}" psql -tAX -U "${APP_DB_USER}" -d "${APP_DB_NAME}" -c "SELECT COALESCE(SUM(amount),0)::TEXT FROM demo_orders;" || echo "0")
if [[ "${SUM_RESTORED}" == "${EXPECTED_SUM}" ]]; then
	CRITICAL_QUERY_STATUS="pass"
	TESTS_PASSED=$((TESTS_PASSED + 1))
else
	CRITICAL_QUERY_STATUS="fail"
fi

# 4) Health endpoint.
# En Fase 4 MVP no existe app restaurada conectada al contenedor de restore,
# por lo que este check se marca como skip para evitar falsos positivos.
HEALTH_STATUS="skip"
HEALTH_REASON="no-restore-app-configured-in-phase4-mvp"
HEALTH_TARGET="not-applicable"

if [[ "${DB_CONNECT_STATUS}" == "pass" && "${ROW_COUNT_STATUS}" == "pass" && "${CRITICAL_QUERY_STATUS}" == "pass" ]]; then
	OVERALL_STATUS="success"
else
	OVERALL_STATUS="fail"
fi

cat > "${RUN_DIR}/smoke-metadata.json" <<EOF
{
	"host": "${HOST_NAME}",
	"backup_label": "${BACKUP_LABEL}",
	"status": "${OVERALL_STATUS}",
	"checks": [
		{"name": "db-connect", "status": "${DB_CONNECT_STATUS}"},
		{"name": "row-count", "status": "${ROW_COUNT_STATUS}", "expected": ${EXPECTED_ROWS}, "actual": ${ROWS_RESTORED}},
		{"name": "critical-query", "status": "${CRITICAL_QUERY_STATUS}", "expected": "${EXPECTED_SUM}", "actual": "${SUM_RESTORED}"},
		{"name": "health-endpoint", "status": "${HEALTH_STATUS}", "target": "${HEALTH_TARGET}", "reason": "${HEALTH_REASON}"}
	],
	"smoke_passed": ${TESTS_PASSED},
	"smoke_total": ${TESTS_TOTAL}
}
EOF

cp "${RUN_DIR}/smoke-metadata.json" "${ART_BASE}/latest-smoke.json"

RESTORE_ID=$(docker exec -i "${META_CONTAINER}" psql -tAX -U "${META_DB_USER}" -d "${META_DB_NAME}" -c "SELECT id FROM restore_run ORDER BY created_at DESC LIMIT 1;" || true)
RESTORE_ID=$(echo "${RESTORE_ID}" | tr -d '[:space:]')

if [[ -n "${RESTORE_ID}" ]]; then
	docker exec -i "${META_CONTAINER}" psql -v ON_ERROR_STOP=1 -U "${META_DB_USER}" -d "${META_DB_NAME}" <<SQL
UPDATE restore_run
SET status = '${OVERALL_STATUS}',
		rows_restored = ${ROWS_RESTORED},
		smoke_passed = ${TESTS_PASSED},
		smoke_total = ${TESTS_TOTAL},
		details_path = '${RUN_DIR}/smoke-metadata.json'
WHERE id = '${RESTORE_ID}';
SQL
fi

echo "Smoke tests completados"
echo "Resultado global: ${OVERALL_STATUS}"
echo "Pruebas aprobadas: ${TESTS_PASSED}/${TESTS_TOTAL}"
echo "Metadata: ${RUN_DIR}/smoke-metadata.json"
