# Fallos conocidos y contingencias (Fase 6)

Fecha base de congelamiento: 2026-04-21

## Fallos conocidos

1. La primera ejecucion de backup/restore puede tardar mas por instalacion de pgBackRest dentro del contenedor postgres-app.
2. Si Docker Desktop/Engine no esta estable, el script de demo puede exceder el objetivo de 7 minutos.
3. El escaneo OpenSCAP en vivo no esta incluido en la demo rapida; para velocidad se usa dataset reproducible importado.
4. El endpoint de dashboard no requiere autenticacion (aceptado para MVP de demostracion).

## Contingencias operativas

1. Si demo_run.sh falla en restore: ejecutar reset_demo.sh y relanzar demo_run.sh.
2. Si falla solo el PDF: relanzar POST /api/reports/evidence/{host} y validar /api/reports/{id}.
3. Si evidence-server no responde: docker compose -f compose/docker-compose.yml up -d evidence-server.
4. Si la metadata esta inconsistente: reset_demo.sh para truncar tablas y volver a seed.

## Criterio de exito para contingencia

- Recuperacion del entorno en menos de 3 minutos usando reset_demo.sh + demo_run.sh.
