#!/bin/bash
set -e

echo "=== Test E2E: Dashboard Administrador ==="

check_url() {
  local url=$1
  local expect=$2
  echo -n "  $url ... "
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" "$url" || echo "000")
  if [ "$code" == "$expect" ]; then
    echo "OK ($code)"
  else
    echo "FAIL (esperado $expect, obtenido $code)"
    exit 1
  fi
}

echo "1. Dashboard HTML carga correctamente"
check_url "http://localhost:8082/" "200"
check_url "http://localhost:8082/dashboard" "200"

echo "2. Dashboard API devuelve JSON válido"
DASHBOARD_JSON=$(curl -s http://localhost:8082/api/dashboard/data)
echo "$DASHBOARD_JSON" | jq -e '.generated_at' > /dev/null 2>&1 || { echo "  FAIL: generated_at no presente"; exit 1; }
echo "$DASHBOARD_JSON" | jq -e '.summary' > /dev/null 2>&1 || { echo "  FAIL: summary no presente"; exit 1; }
echo "$DASHBOARD_JSON" | jq -e '.primary_host' > /dev/null 2>&1 || { echo "  FAIL: primary_host no presente"; exit 1; }
echo "$DASHBOARD_JSON" | jq -e '.hosts' > /dev/null 2>&1 || { echo "  FAIL: hosts no presente"; exit 1; }
echo "$DASHBOARD_JSON" | jq -e '.actions' > /dev/null 2>&1 || { echo "  FAIL: actions no presente"; exit 1; }
echo "$DASHBOARD_JSON" | jq -e '.timeline' > /dev/null 2>&1 || { echo "  FAIL: timeline no presente"; exit 1; }
echo "  OK: JSON estructura correcta"

echo "3. APIs legacy siguen funcionando"
check_url "http://localhost:8082/api/hosts" "200"
check_url "http://localhost:8082/api/evidence/overview" "200"

echo "4. Acciones configuradas correctamente"
GEN_ENABLED=$(echo "$DASHBOARD_JSON" | jq -r '.actions.generate_report.enabled')
BK_ENABLED=$(echo "$DASHBOARD_JSON" | jq -r '.actions.execute_backup.enabled')
if [ "$GEN_ENABLED" != "true" ]; then
  echo "  FAIL: generate_report debería estar habilitado"
  exit 1
fi
if [ "$BK_ENABLED" != "false" ]; then
  echo "  FAIL: execute_backup debería estar deshabilitado"
  exit 1
fi
echo "  OK: Acciones correctamente habilitadas/deshabilitadas"

echo "5. Estado consistente entre API y dashboard"
STATUS_HOSTS=$(curl -s http://localhost:8082/api/hosts | jq -r '.[0].evidence_status // "none"')
STATUS_DASH=$(echo "$DASHBOARD_JSON" | jq -r '.primary_host.evidence_status // "none"')
if [ "$STATUS_HOSTS" != "$STATUS_DASH" ]; then
  echo "  FAIL: Estado inconsistente hosts=$STATUS_HOSTS dashboard=$STATUS_DASH"
  exit 1
fi
echo "  OK: Estados consistentes ($STATUS_HOSTS)"

echo "6. HTML contiene assets estáticos"
HTML=$(curl -s http://localhost:8082/dashboard)
echo "$HTML" | grep -q 'dashboard.css' || { echo "  FAIL: CSS no referenciado"; exit 1; }
echo "$HTML" | grep -q 'dashboard.js' || { echo "  FAIL: JS no referenciado"; exit 1; }
echo "  OK: Assets referenciados"

echo "=== Todas las validaciones del dashboard pasaron ==="
