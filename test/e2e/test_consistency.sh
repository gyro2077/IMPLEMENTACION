#!/bin/bash
set -e

echo "=== Verificando consistencia de estados ==="

HOSTS_API="http://localhost:8082/api/hosts"
OVERVIEW_API="http://localhost:8082/api/evidence/overview"

echo "1. Obteniendo datos de API /hosts..."
HOSTS_JSON=$(curl -s "$HOSTS_API")

echo "2. Obteniendo datos de API /evidence/overview..."
OVERVIEW_JSON=$(curl -s "$OVERVIEW_API")

# Extraer el estado del host principal (target-cachyos) de ambas APIs
STATUS_HOSTS=$(echo "$HOSTS_JSON" | jq -r '.[] | select(.host == "target-cachyos") | .evidence_status')
STATUS_OVERVIEW=$(echo "$OVERVIEW_JSON" | jq -r '.hosts[] | select(.host == "target-cachyos") | .evidence_status')

echo "Estado en /hosts: $STATUS_HOSTS"
echo "Estado en /overview: $STATUS_OVERVIEW"

if [ -z "$STATUS_HOSTS" ] || [ "$STATUS_HOSTS" == "null" ]; then
  echo "ERROR: No se encontro el estado en /hosts."
  exit 1
fi

if [ "$STATUS_HOSTS" != "$STATUS_OVERVIEW" ]; then
  echo "ERROR: Inconsistencia detectada. $STATUS_HOSTS != $STATUS_OVERVIEW"
  exit 1
fi

if [ "$STATUS_HOSTS" == "problem" ]; then
  echo "ERROR: El estado sigue siendo 'problem' (Posiblemente la nueva logica no se aplico o hay fallas reales)."
  exit 1
fi

echo "OK: El estado es consistente y no es 'problem' injustificado."

# Adicionalmente, podemos verificar la presencia de evidence_status_label
LABEL_HOSTS=$(echo "$HOSTS_JSON" | jq -r '.[] | select(.host == "target-cachyos") | .evidence_status_label')
if [ -z "$LABEL_HOSTS" ] || [ "$LABEL_HOSTS" == "null" ]; then
  echo "ERROR: No se encontro evidence_status_label"
  exit 1
fi

echo "Label encontrado: $LABEL_HOSTS"
echo "=== Consistencia validada ==="
