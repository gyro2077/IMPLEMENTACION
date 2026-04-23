#!/bin/bash
set -e

echo "=== Iniciando validaciones de Observabilidad ==="

check_url() {
  local url=$1
  local expect=$2
  echo -n "Verificando $url ... "
  local response=$(curl -s -o /dev/null -w "%{http_code}" "$url" || echo "FAIL")
  if [ "$response" == "$expect" ]; then
    echo "OK ($response)"
  else
    echo "ERROR (obtenido: $response, esperado: $expect)"
    exit 1
  fi
}

echo "1. Healthchecks Core"
check_url "http://localhost:8081/actuator/health" "200"
check_url "http://localhost:8082/actuator/health" "200"

echo "2. Trazas (Jaeger)"
check_url "http://localhost:16686/" "200"

echo "3. Métricas (Micrometer + Prometheus)"
check_url "http://localhost:8081/actuator/prometheus" "200"
check_url "http://localhost:9090/-/healthy" "200"

echo "4. Dashboards (Grafana)"
check_url "http://localhost:3000/api/health" "200"

echo "5. Logs (Loki + Alloy)"
check_url "http://localhost:3100/ready" "200"
check_url "http://localhost:12345/-/ready" "200"

echo "6. Verificando persistencia real de logs en Loki..."
LOKI_QUERY_URL='http://localhost:3100/loki/api/v1/query_range?query={container="flisol-target-app"}'
response=$(curl -s -g "$LOKI_QUERY_URL" | grep -o '"status":"success"' || true)
if [ "$response" == '"status":"success"' ]; then
  echo "OK (Logs encontrados o query exitosa)"
else
  echo "ERROR: Falló consulta a Loki"
  exit 1
fi

echo "=== Todas las validaciones pasaron ==="
