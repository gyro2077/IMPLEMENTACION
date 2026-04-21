#!/usr/bin/env bash
set -euo pipefail

# Fase 4: restore en contenedor aislado usando pgBackRest.
# Uso:
#   ./automation/scripts/restore_db.sh [backup_metadata_json]

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "${SCRIPT_DIR}/../.." && pwd)
ENV_FILE="${REPO_ROOT}/compose/.env"

if [[ -f "${ENV_FILE}" ]]; then
	set -a
	# shellcheck disable=SC1090
	source "${ENV_FILE}"
	set +a
fi

APP_CONTAINER="flisol-postgres-app"
META_CONTAINER="flisol-postgres-metadata"
RESTORE_CONTAINER="flisol-postgres-restore"
RESTORE_WORKER="flisol-pgbackrest-restore-worker"
STANZA="appdb"

BACKUP_META_FILE=${1:-"${REPO_ROOT}/fixtures/pgbackrest/latest-backup.json"}
if [[ ! -f "${BACKUP_META_FILE}" ]]; then
	echo "No existe metadata de backup: ${BACKUP_META_FILE}"
	exit 1
fi

HOST_NAME=$(python3 - <<PY
import json
from pathlib import Path
data = json.loads(Path("${BACKUP_META_FILE}").read_text(encoding="utf-8"))
print(data.get("host", "target-local"))
PY
)

BACKUP_LABEL=$(python3 - <<PY
import json
from pathlib import Path
data = json.loads(Path("${BACKUP_META_FILE}").read_text(encoding="utf-8"))
print(data.get("backup_label", ""))
PY
)

DATA_DIRECTORY=$(python3 - <<PY
import json
from pathlib import Path
data = json.loads(Path("${BACKUP_META_FILE}").read_text(encoding="utf-8"))
print(data.get("data_directory", "/var/lib/postgresql/18/docker"))
PY
)

EXPECTED_ROWS=$(python3 - <<PY
import json
from pathlib import Path
data = json.loads(Path("${BACKUP_META_FILE}").read_text(encoding="utf-8"))
print(int(data.get("expected_rows", 0)))
PY
)

ART_BASE="${REPO_ROOT}/fixtures/pgbackrest"
RUN_TS=$(date -u +%Y%m%dT%H%M%SZ)
RUN_DIR="${ART_BASE}/${HOST_NAME}/restore-${RUN_TS}"
RESTORE_DATA_DIR="${ART_BASE}/restore-data"
mkdir -p "${RUN_DIR}" "${RESTORE_DATA_DIR}"

if ! docker ps --format '{{.Names}}' | grep -q "^${APP_CONTAINER}$"; then
	echo "No esta activo ${APP_CONTAINER}. Levanta docker compose primero."
	exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -q "^${META_CONTAINER}$"; then
	echo "No esta activo ${META_CONTAINER}. Levanta docker compose primero."
	exit 1
fi

if [[ ! -d "${ART_BASE}/repo/current" ]]; then
	echo "No existe repo de backup en ${ART_BASE}/repo/current. Ejecuta backup_db.sh primero."
	exit 1
fi

# Simula incidente controlado en la DB origen (sin tocar estructura).
docker exec -i -u postgres "${APP_CONTAINER}" psql -v ON_ERROR_STOP=1 -U "${APP_DB_USER}" -d "${APP_DB_NAME}" <<'SQL'
DO $$
BEGIN
	IF to_regclass('public.demo_orders') IS NOT NULL THEN
		TRUNCATE TABLE demo_orders;
	END IF;
END $$;
SQL

INCIDENT_ROWS=$(docker exec -u postgres "${APP_CONTAINER}" psql -tAX -U "${APP_DB_USER}" -d "${APP_DB_NAME}" -c "SELECT COUNT(*) FROM demo_orders;")

docker rm -f "${RESTORE_CONTAINER}" >/dev/null 2>&1 || true
docker rm -f "${RESTORE_WORKER}" >/dev/null 2>&1 || true
docker run --rm -v "${RESTORE_DATA_DIR}:/cleanup" postgres:18.3 bash -lc 'rm -rf /cleanup/*'

cat > "${RUN_DIR}/pgbackrest-restore.conf" <<EOF
[global]
repo1-path=/var/lib/pgbackrest
repo1-retention-full=2
log-level-console=info

[${STANZA}]
pg1-path=${DATA_DIRECTORY}
pg1-port=5432
EOF

RESTORE_STARTED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
RESTORE_START_SEC=$(date +%s)

docker run --rm --name "${RESTORE_WORKER}" \
	-v "${ART_BASE}/repo/current:/var/lib/pgbackrest" \
	-v "${RESTORE_DATA_DIR}:/var/lib/postgresql" \
	postgres:18.3 \
	bash -lc "
set -e
if ! command -v pgbackrest >/dev/null 2>&1; then
	apt-get update
	DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends pgbackrest
	rm -rf /var/lib/apt/lists/*
fi
mkdir -p /etc/pgbackrest
cat > /etc/pgbackrest/pgbackrest.conf <<EOF
[global]
repo1-path=/var/lib/pgbackrest
repo1-retention-full=2
start-fast=y
process-max=2
log-level-console=info

[${STANZA}]
pg1-path=${DATA_DIRECTORY}
pg1-port=5432
EOF
chown -R postgres:postgres /var/lib/postgresql /var/lib/pgbackrest /etc/pgbackrest
gosu postgres pgbackrest --stanza=${STANZA} restore
"

docker run -d --name "${RESTORE_CONTAINER}" \
	--network compose_default \
	-p 5544:5432 \
	-e POSTGRES_DB="${APP_DB_NAME}" \
	-e POSTGRES_USER="${APP_DB_USER}" \
	-e POSTGRES_PASSWORD="${APP_DB_PASSWORD}" \
	-v "${ART_BASE}/repo/current:/var/lib/pgbackrest" \
	-v "${RUN_DIR}/pgbackrest-restore.conf:/etc/pgbackrest/pgbackrest.conf:ro" \
	-v "${RESTORE_DATA_DIR}:/var/lib/postgresql" \
	--entrypoint bash \
	postgres:18.3 \
	-lc "
set -e
if ! command -v pgbackrest >/dev/null 2>&1; then
	apt-get update
	DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends pgbackrest
	rm -rf /var/lib/apt/lists/*
fi
export PATH="/usr/lib/postgresql/18/bin:${PATH}"
exec docker-entrypoint.sh postgres
" >/dev/null

READY=$(python3 - <<PY
import subprocess
import time

cmd = [
		"docker", "exec", "${RESTORE_CONTAINER}",
		"pg_isready", "-U", "${APP_DB_USER}", "-d", "${APP_DB_NAME}"
]
for _ in range(180):
		if subprocess.call(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL) == 0:
				print("1")
				raise SystemExit(0)
		time.sleep(1)
print("0")
PY
)

if [[ "${READY}" != "1" ]]; then
	echo "El contenedor de restore no quedo listo."
	exit 1
fi

RESTORE_FINISHED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
RESTORE_END_SEC=$(date +%s)
RTO_SECONDS=$((RESTORE_END_SEC - RESTORE_START_SEC))

cat > "${RUN_DIR}/restore-metadata.json" <<EOF
{
	"host": "${HOST_NAME}",
	"backup_label": "${BACKUP_LABEL}",
	"status": "restored",
	"started_at": "${RESTORE_STARTED_AT}",
	"finished_at": "${RESTORE_FINISHED_AT}",
	"rto_seconds": ${RTO_SECONDS},
	"rows_expected": ${EXPECTED_ROWS},
	"incident_rows_after_truncate": ${INCIDENT_ROWS},
	"restore_container": "${RESTORE_CONTAINER}",
	"restore_data_dir": "${RESTORE_DATA_DIR}"
}
EOF

cp "${RUN_DIR}/restore-metadata.json" "${ART_BASE}/latest-restore.json"

RESTORE_ID=$(python3 - <<'PY'
import uuid
print(uuid.uuid4())
PY
)

DETAILS_PATH="${RUN_DIR}/restore-metadata.json"

docker exec -i "${META_CONTAINER}" psql -v ON_ERROR_STOP=1 -U "${META_DB_USER}" -d "${META_DB_NAME}" <<SQL
CREATE TABLE IF NOT EXISTS restore_run (
	id UUID PRIMARY KEY,
	host_name VARCHAR(120) NOT NULL,
	backup_label VARCHAR(120),
	status VARCHAR(30) NOT NULL,
	rows_expected INTEGER,
	rows_restored INTEGER,
	rto_seconds INTEGER,
	smoke_passed INTEGER,
	smoke_total INTEGER,
	started_at TIMESTAMPTZ,
	finished_at TIMESTAMPTZ,
	details_path TEXT,
	created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO restore_run (
	id, host_name, backup_label, status,
	rows_expected, rows_restored, rto_seconds,
	started_at, finished_at, details_path
) VALUES (
	'${RESTORE_ID}', '${HOST_NAME}', '${BACKUP_LABEL}', 'restored',
	${EXPECTED_ROWS}, NULL, ${RTO_SECONDS},
	'${RESTORE_STARTED_AT}', '${RESTORE_FINISHED_AT}', '${DETAILS_PATH}'
);
SQL

echo "Restore completado en entorno aislado"
echo "Contenedor restore: ${RESTORE_CONTAINER}"
echo "RTO (s): ${RTO_SECONDS}"
echo "Metadata: ${RUN_DIR}/restore-metadata.json"
echo "Siguiente paso: ./automation/scripts/smoke_test_restore.sh"
