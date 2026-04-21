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
HARD_RESET=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)
      HOST_NAME="$2"
      shift 2
      ;;
    --hard)
      HARD_RESET=1
      shift
      ;;
    *)
      echo "Parametro no soportado: $1"
      exit 1
      ;;
  esac
done

META_CONTAINER="flisol-postgres-metadata"
APP_CONTAINER="flisol-postgres-app"

echo "[reset] Limpiando contenedores de restore"
docker rm -f flisol-postgres-restore >/dev/null 2>&1 || true
docker rm -f flisol-pgbackrest-restore-worker >/dev/null 2>&1 || true

echo "[reset] Limpiando artefactos de demo"
rm -rf "${REPO_ROOT}/fixtures/pgbackrest/${HOST_NAME}/restore-"*
rm -rf "${REPO_ROOT}/fixtures/pgbackrest/${HOST_NAME}/smoke-"*
rm -f "${REPO_ROOT}/fixtures/pgbackrest/latest-restore.json"
rm -f "${REPO_ROOT}/fixtures/pgbackrest/latest-smoke.json"
rm -f "${REPO_ROOT}/reports/generated/demo-"*.json
rm -f "${REPO_ROOT}/reports/generated/demo-"*.md
rm -f "${REPO_ROOT}/reports/generated/demo-"*.pdf

if docker ps --format '{{.Names}}' | grep -q "^${META_CONTAINER}$"; then
  echo "[reset] Limpiando tablas de metadata"
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
else
  echo "[reset] Metadata DB no activa, se omite truncado"
fi

if docker ps --format '{{.Names}}' | grep -q "^${APP_CONTAINER}$"; then
  echo "[reset] Limpiando tabla demo_orders en app DB"
  docker exec -i -u postgres "${APP_CONTAINER}" psql -v ON_ERROR_STOP=1 -U "${APP_DB_USER}" -d "${APP_DB_NAME}" <<'SQL'
DO $$
BEGIN
  IF to_regclass('public.demo_orders') IS NOT NULL THEN
    TRUNCATE TABLE demo_orders;
  END IF;
END $$;
SQL
fi

if [[ "${HARD_RESET}" -eq 1 ]]; then
  echo "[reset] HARD reset: docker compose down --volumes"
  docker compose -f "${REPO_ROOT}/compose/docker-compose.yml" down --remove-orphans --volumes
fi

echo "[reset] Entorno demo limpio"
