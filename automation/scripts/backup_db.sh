#!/usr/bin/env bash
set -euo pipefail

# Fase 4: backup full con pgBackRest sobre postgres-app.
# Uso:
#   ./automation/scripts/backup_db.sh [host_name]

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
STANZA="appdb"
HOST_NAME=${1:-target-cachyos}

RUN_TS=$(date -u +%Y%m%dT%H%M%SZ)
ART_BASE="${REPO_ROOT}/fixtures/pgbackrest"
RUN_DIR="${ART_BASE}/${HOST_NAME}/${RUN_TS}"
REPO_SNAPSHOT_CURRENT_DIR="${ART_BASE}/repo/current"
REPO_SNAPSHOT_HISTORY_BASE="${ART_BASE}/repo/history"
mkdir -p "${RUN_DIR}" "${REPO_SNAPSHOT_CURRENT_DIR}" "${REPO_SNAPSHOT_HISTORY_BASE}"

if ! docker ps --format '{{.Names}}' | grep -q "^${APP_CONTAINER}$"; then
	echo "No esta activo ${APP_CONTAINER}. Levanta docker compose primero."
	exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -q "^${META_CONTAINER}$"; then
	echo "No esta activo ${META_CONTAINER}. Levanta docker compose primero."
	exit 1
fi

# Instala pgBackRest dentro del contenedor de PostgreSQL si no existe.
docker exec -u root "${APP_CONTAINER}" bash -lc '
set -e
if ! command -v pgbackrest >/dev/null 2>&1; then
	apt-get update
	DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends pgbackrest
	rm -rf /var/lib/apt/lists/*
fi
'

DATA_DIR=$(docker exec -u postgres "${APP_CONTAINER}" psql -tAX -U "${APP_DB_USER}" -d "${APP_DB_NAME}" -c "SHOW data_directory;")
if [[ -z "${DATA_DIR}" ]]; then
	echo "No fue posible detectar data_directory en postgres-app"
	exit 1
fi

# Seed de datos demo para validar consistencia post-restore.
docker exec -i -u postgres "${APP_CONTAINER}" psql -v ON_ERROR_STOP=1 -U "${APP_DB_USER}" -d "${APP_DB_NAME}" <<'SQL'
CREATE TABLE IF NOT EXISTS demo_orders (
	id BIGSERIAL PRIMARY KEY,
	order_code TEXT UNIQUE NOT NULL,
	amount NUMERIC(10,2) NOT NULL,
	created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO demo_orders (order_code, amount)
SELECT 'ORD-' || to_char(g, 'FM000000'), (g * 10.50)::NUMERIC(10,2)
FROM generate_series(1, 20) g
ON CONFLICT (order_code) DO NOTHING;
SQL

# Compatibilidad: algunos initdb no crean rol postgres si POSTGRES_USER es custom.
docker exec -i -u postgres "${APP_CONTAINER}" psql -v ON_ERROR_STOP=1 -U "${APP_DB_USER}" -d "${APP_DB_NAME}" <<'SQL'
DO $$
BEGIN
	IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres') THEN
		CREATE ROLE postgres WITH LOGIN SUPERUSER;
	END IF;
END $$;
SQL

EXPECTED_ROWS=$(docker exec -u postgres "${APP_CONTAINER}" psql -tAX -U "${APP_DB_USER}" -d "${APP_DB_NAME}" -c "SELECT COUNT(*) FROM demo_orders;")
EXPECTED_SUM=$(docker exec -u postgres "${APP_CONTAINER}" psql -tAX -U "${APP_DB_USER}" -d "${APP_DB_NAME}" -c "SELECT COALESCE(SUM(amount),0)::TEXT FROM demo_orders;")

# Configuracion minima de pgBackRest para backup local en el contenedor DB.
docker exec -u root "${APP_CONTAINER}" bash -lc "
set -e
mkdir -p /etc/pgbackrest /var/lib/pgbackrest
cat > /etc/pgbackrest/pgbackrest.conf <<EOF
[global]
repo1-path=/var/lib/pgbackrest
repo1-retention-full=2
start-fast=y
process-max=2
log-level-console=info

[${STANZA}]
pg1-path=${DATA_DIR}
pg1-port=5432
pg1-user=${APP_DB_USER}
EOF
chown postgres:postgres /etc/pgbackrest/pgbackrest.conf
chown -R postgres:postgres /var/lib/pgbackrest
"

BACKUP_STARTED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
BACKUP_START_SEC=$(date +%s)

docker exec -u postgres "${APP_CONTAINER}" pgbackrest --stanza="${STANZA}" stanza-create
CHECK_STATUS="success"
CHECK_EXIT_CODE=0
set +e
docker exec -u postgres "${APP_CONTAINER}" pgbackrest --stanza="${STANZA}" --archive-check=n check
CHECK_EXIT_CODE=$?
set -e
if [[ "${CHECK_EXIT_CODE}" -ne 0 ]]; then
	CHECK_STATUS="warning"
	echo "Aviso: pgbackrest check retorno ${CHECK_EXIT_CODE}. En modo MVP continuamos al backup full."
fi

docker exec -u postgres "${APP_CONTAINER}" pgbackrest --stanza="${STANZA}" --archive-check=n --type=full backup
INFO_JSON=$(docker exec -u postgres "${APP_CONTAINER}" pgbackrest --stanza="${STANZA}" info --output=json)

PARSED=$(JSON_INPUT="${INFO_JSON}" python3 - <<'PY'
import json
import os
import sys

data = json.loads(os.environ.get("JSON_INPUT", "[]"))
backup = (data[0].get("backup") or []) if data else []
if not backup:
		print("|0||")
		raise SystemExit(0)

latest = backup[-1]
label = latest.get("label", "")
size = 0
info = latest.get("info") or {}
archive = latest.get("archive") or {}
archive_start = archive.get("start", "")
archive_stop = archive.get("stop", "")
if isinstance(info.get("repository"), dict):
		size = info["repository"].get("size", 0)
if not size:
		size = info.get("size", 0)
print(f"{label}|{int(size)}|{archive_start}|{archive_stop}")
PY
)

BACKUP_LABEL=${PARSED%%|*}
REST=${PARSED#*|}
SIZE_BYTES=${REST%%|*}
REST=${REST#*|}
ARCHIVE_START=${REST%%|*}
ARCHIVE_STOP=${REST##*|}
BACKUP_KEY=${BACKUP_LABEL:-${RUN_TS}}
REPO_SNAPSHOT_HISTORY_DIR="${REPO_SNAPSHOT_HISTORY_BASE}/${BACKUP_KEY}"

# En modo MVP sin archive_command continuo, empuja WAL del backup para habilitar restore.
ARCHIVE_PUSH_STATUS="success"
if [[ -n "${ARCHIVE_START}" || -n "${ARCHIVE_STOP}" ]]; then
	docker exec -u postgres "${APP_CONTAINER}" psql -tAX -U "${APP_DB_USER}" -d "${APP_DB_NAME}" -c "SELECT pg_switch_wal();" >/dev/null || true
	for SEG in "${ARCHIVE_START}" "${ARCHIVE_STOP}"; do
		[[ -z "${SEG}" ]] && continue
		if ! docker exec -u postgres "${APP_CONTAINER}" test -f "${DATA_DIR}/pg_wal/${SEG}"; then
			echo "Aviso: WAL ${SEG} no esta disponible para archive-push."
			ARCHIVE_PUSH_STATUS="warning"
			continue
		fi
		if ! docker exec -u postgres "${APP_CONTAINER}" pgbackrest --stanza="${STANZA}" archive-push "${DATA_DIR}/pg_wal/${SEG}"; then
			echo "Aviso: no se pudo hacer archive-push de ${SEG}."
			ARCHIVE_PUSH_STATUS="warning"
		fi
	done
else
	ARCHIVE_PUSH_STATUS="skipped"
fi

BACKUP_FINISHED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
BACKUP_END_SEC=$(date +%s)
DURATION_SECONDS=$((BACKUP_END_SEC - BACKUP_START_SEC))

# Copia artefactos de respaldo para evidencia local del repo.
mkdir -p "${ART_BASE}/repo" "${REPO_SNAPSHOT_CURRENT_DIR}" "${REPO_SNAPSHOT_HISTORY_DIR}"
HOST_UID=$(id -u)
HOST_GID=$(id -g)
docker run --rm -v "${ART_BASE}/repo:/repo" postgres:18.3 bash -lc "mkdir -p /repo/current /repo/history/${BACKUP_KEY} && rm -rf /repo/current/* /repo/history/${BACKUP_KEY}/* && chown -R ${HOST_UID}:${HOST_GID} /repo/current /repo/history/${BACKUP_KEY}"
docker cp "${APP_CONTAINER}":/var/lib/pgbackrest/. "${REPO_SNAPSHOT_HISTORY_DIR}/"
docker cp "${APP_CONTAINER}":/var/lib/pgbackrest/. "${REPO_SNAPSHOT_CURRENT_DIR}/"
docker cp "${APP_CONTAINER}":/etc/pgbackrest/pgbackrest.conf "${RUN_DIR}/pgbackrest.conf"

cat > "${RUN_DIR}/backup-metadata.json" <<EOF
{
	"host": "${HOST_NAME}",
	"stanza": "${STANZA}",
	"backup_type": "full",
	"check_status": "${CHECK_STATUS}",
	"check_exit_code": ${CHECK_EXIT_CODE},
	"backup_label": "${BACKUP_LABEL}",
	"status": "success",
	"size_bytes": ${SIZE_BYTES},
	"archive_start": "${ARCHIVE_START}",
	"archive_stop": "${ARCHIVE_STOP}",
	"archive_push_status": "${ARCHIVE_PUSH_STATUS}",
	"duration_seconds": ${DURATION_SECONDS},
	"started_at": "${BACKUP_STARTED_AT}",
	"finished_at": "${BACKUP_FINISHED_AT}",
	"data_directory": "${DATA_DIR}",
	"repo_path": "${REPO_SNAPSHOT_HISTORY_DIR}",
	"repo_path_current": "${REPO_SNAPSHOT_CURRENT_DIR}",
	"expected_rows": ${EXPECTED_ROWS},
	"expected_sum": "${EXPECTED_SUM}"
}
EOF

cp "${RUN_DIR}/backup-metadata.json" "${ART_BASE}/latest-backup.json"

BACKUP_ID=$(python3 - <<'PY'
import uuid
print(uuid.uuid4())
PY
)

DETAILS_PATH="${RUN_DIR}/backup-metadata.json"
REPO_PATH="${REPO_SNAPSHOT_HISTORY_DIR}"

docker exec -i "${META_CONTAINER}" psql -v ON_ERROR_STOP=1 -U "${META_DB_USER}" -d "${META_DB_NAME}" <<SQL
CREATE TABLE IF NOT EXISTS backup_run (
	id UUID PRIMARY KEY,
	host_name VARCHAR(120) NOT NULL,
	backup_type VARCHAR(30) NOT NULL,
	backup_label VARCHAR(120),
	status VARCHAR(30) NOT NULL,
	size_bytes BIGINT,
	duration_seconds INTEGER,
	started_at TIMESTAMPTZ,
	finished_at TIMESTAMPTZ,
	repo_path TEXT,
	details_path TEXT,
	created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO backup_run (
	id, host_name, backup_type, backup_label, status,
	size_bytes, duration_seconds, started_at, finished_at,
	repo_path, details_path
) VALUES (
	'${BACKUP_ID}', '${HOST_NAME}', 'full', '${BACKUP_LABEL}', 'success',
	${SIZE_BYTES}, ${DURATION_SECONDS}, '${BACKUP_STARTED_AT}', '${BACKUP_FINISHED_AT}',
	'${REPO_PATH}', '${DETAILS_PATH}'
);
SQL

echo "Backup full completado"
echo "Label: ${BACKUP_LABEL}"
echo "Duracion (s): ${DURATION_SECONDS}"
echo "Tamano (bytes): ${SIZE_BYTES}"
echo "Metadata: ${RUN_DIR}/backup-metadata.json"
