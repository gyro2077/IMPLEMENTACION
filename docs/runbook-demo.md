# Runbook de demo - Fase 6

Objetivo: ejecutar la demo completa (observabilidad + compliance + backup/restore + reporte PDF) con un solo comando principal y narrativa para audiencia no tecnica.

## Flujo recomendado (demo day)

1. Levantar stack (si no esta arriba):
	- `docker compose -f compose/docker-compose.yml up -d --build`
2. Ejecutar demo completa:
	- `./automation/scripts/demo_run.sh`
3. Mostrar dashboard no tecnico:
	- `http://localhost:8082/dashboard`
4. Mostrar PDF generado:
	- Archivo en `reports/generated/demo-report-<timestamp>.pdf`
5. Mostrar metricas del ensayo:
	- `reports/generated/demo-last-run.json`

## Guion de narrativa (7 minutos)

1. Min 0:00-0:45
	- "La plataforma junta evidencia de cumplimiento, respaldo y recuperacion en un solo panel."
2. Min 0:45-2:00
	- Ejecutar `demo_run.sh` y explicar que automatiza seed, backup, restore, smoke y PDF.
3. Min 2:00-4:00
	- Abrir dashboard y destacar:
	  - tarjeta de estado general,
	  - host principal,
	  - leyenda de compliance/backup/restore/RTO.
4. Min 4:00-5:30
	- Mostrar tabla de restores y valor RTO.
5. Min 5:30-6:30
	- Mostrar PDF de evidencia final descargable.
6. Min 6:30-7:00
	- Cerrar con metricas: tiempo total, comandos manuales y recuperacion.

## Comandos de ensayo

1. Reset rapido:
	- `./automation/scripts/reset_demo.sh`
2. Ensayo 1:
	- `./automation/scripts/demo_run.sh`
3. Ensayo 2 (sin tocar codigo):
	- `./automation/scripts/demo_run.sh`

## Criterios de aceptacion operativos

1. Tiempo total del run: `total_seconds <= 420` en `demo-last-run.json`.
2. Comandos manuales: `manual_commands <= 2`.
3. Fallos: `failures == 0`.
4. PDF generado y descargable.
5. Dashboard entendible para audiencia no tecnica.

## Contingencia rapida

1. Si falla el flujo:
	- `./automation/scripts/reset_demo.sh`
	- `./automation/scripts/demo_run.sh`
2. Si falla solo reporte PDF:
	- `curl -X POST http://localhost:8082/api/reports/evidence/target-cachyos`
3. Si evidence-server cae:
	- `docker compose -f compose/docker-compose.yml up -d evidence-server`

## Artefactos de salida por corrida

1. `reports/generated/demo-last-run.json`
2. `reports/generated/demo-last-run.md`
3. `reports/generated/demo-report-<timestamp>.pdf`
4. `fixtures/pgbackrest/<host>/...` (backup/restore/smoke metadata)

## Regla de congelamiento

- No cambiar dependencias ni arquitectura dentro de las 48 horas previas al evento.
