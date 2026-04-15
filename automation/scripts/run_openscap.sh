#!/usr/bin/env bash
set -euo pipefail

# Fase 3A: escaneo manual y controlado con baseline pequena.
# Uso:
#   ./automation/scripts/run_openscap.sh [host_name] [profile_id] [datastream_xml]
# Ejemplo:
#   ./automation/scripts/run_openscap.sh target-01 \
#     xccdf_org.ssgproject.content_profile_standard \
#     /usr/share/xml/scap/ssg/content/ssg-ubuntu2404-ds.xml

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "${SCRIPT_DIR}/../.." && pwd)

HOST_NAME=${1:-target-local}
PROFILE_ID=${2:-xccdf_org.ssgproject.content_profile_cis_level1_server}
DATASTREAM_XML=${3:-}

if [[ -z "${DATASTREAM_XML}" ]]; then
	# Seleccion simple y explicable para Arch/CachyOS: usar contenido Ubuntu disponible.
	CANDIDATES=(
		/usr/share/xml/scap/ssg/content/ssg-ubuntu2404-ds.xml
		/usr/share/xml/scap/ssg/content/ssg-ubuntu2204-ds.xml
		/usr/share/xml/scap/ssg/content/ssg-debian12-ds.xml
		/usr/share/xml/scap/ssg/content/ssg-fedora-ds.xml
	)
	for candidate in "${CANDIDATES[@]}"; do
		if [[ -f "${candidate}" ]]; then
			DATASTREAM_XML="${candidate}"
			break
		fi
	done
fi

if [[ -z "${DATASTREAM_XML}" ]]; then
	echo "No se pudo detectar un datastream automaticamente."
	echo "Instala contenido SCAP (Arch/CachyOS): paru -S scap-security-guide"
	echo "Uso manual: $0 <host_name> <profile_id> <datastream_xml>"
	exit 1
fi

if [[ ! -f "${DATASTREAM_XML}" ]]; then
	echo "No existe el datastream: ${DATASTREAM_XML}"
	exit 1
fi

if ! command -v oscap >/dev/null 2>&1; then
	echo "No se encontro oscap en PATH. Instala OpenSCAP antes de continuar."
	exit 1
fi

RUN_TS=$(date -u +%Y%m%dT%H%M%SZ)
STARTED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
OUT_DIR="${REPO_ROOT}/fixtures/openscap/${HOST_NAME}/${RUN_TS}"
ARF_PATH="${OUT_DIR}/scan-results.arf.xml"
HTML_PATH="${OUT_DIR}/scan-report.html"

mkdir -p "${OUT_DIR}"

OSCAP_CMD=(oscap)
if [[ "$(id -u)" -ne 0 ]]; then
	if command -v sudo >/dev/null 2>&1; then
		OSCAP_CMD=(sudo oscap)
	fi
fi

echo "Ejecutando OpenSCAP..."
echo "Host: ${HOST_NAME}"
echo "Profile: ${PROFILE_ID}"
echo "Datastream: ${DATASTREAM_XML}"

set +e
"${OSCAP_CMD[@]}" xccdf eval \
	--profile "${PROFILE_ID}" \
	--results-arf "${ARF_PATH}" \
	--report "${HTML_PATH}" \
	"${DATASTREAM_XML}"
OSCAP_EXIT=$?
set -e

# OpenSCAP puede retornar 2 cuando hay reglas fallidas; no es error tecnico del scan.
if [[ "${OSCAP_EXIT}" -ne 0 && "${OSCAP_EXIT}" -ne 2 ]]; then
  echo "OpenSCAP termino con codigo no esperado: ${OSCAP_EXIT}"
  exit "${OSCAP_EXIT}"
fi

FINISHED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)

# Metadata minima para rastrear esta ejecucion manual.
cat > "${OUT_DIR}/run-metadata.json" <<EOF
{
	"host": "${HOST_NAME}",
	"profile_id": "${PROFILE_ID}",
	"oscap_exit_code": ${OSCAP_EXIT},
	"datastream_xml": "${DATASTREAM_XML}",
	"started_at": "${STARTED_AT}",
	"finished_at": "${FINISHED_AT}",
	"arf_path": "${ARF_PATH}",
	"html_report_path": "${HTML_PATH}"
}
EOF

echo "Scan completado"
echo "ARF/XML: ${ARF_PATH}"
echo "HTML: ${HTML_PATH}"
echo "Metadata: ${OUT_DIR}/run-metadata.json"
