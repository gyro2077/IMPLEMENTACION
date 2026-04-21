# Congelamiento de version para demo (Fase 6)

Fecha de congelamiento UTC: 2026-04-21T15:23:58Z

## Referencia de codigo

- Commit corto: 916dbf6
- Commit completo: 916dbf68fbcf46bd07e961e5f31423bfcb767bdd

## Componentes congelados para demo

1. Arquitectura Compose actual (2 apps + 2 PostgreSQL + observabilidad).
2. Flujo de evidencia de Fases 1 a 5 sin cambios estructurales.
3. Scripts de Fase 6:
   - `automation/scripts/seed_demo_data.sh`
   - `automation/scripts/demo_run.sh`
   - `automation/scripts/reset_demo.sh`
4. Dashboard HTML server-side en evidence-server (sin frontend pesado).

## Regla operativa

- No cambiar dependencias ni arquitectura 48 horas antes del evento.
- Solo se permiten ajustes de texto/documentacion no funcionales.

## Comando sugerido para tag de demo

1. Crear tag local:
   - `git tag -a demo-freeze-2026-04-21 -m "Demo freeze Fase 6"`
2. Publicar tag:
   - `git push origin demo-freeze-2026-04-21`

## Checklist de pre-demo

1. `test/integration/integration_phase6.sh` pasa.
2. `test/e2e/e2e_phase6_demo.sh` pasa.
3. Dos ensayos consecutivos con `demo_run.sh` sin tocar codigo.
4. `reports/generated/demo-last-run.json` dentro del objetivo de tiempo.
