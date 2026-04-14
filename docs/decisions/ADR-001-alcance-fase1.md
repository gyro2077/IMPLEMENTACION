# ADR-001: Alcance de Fase 1

## Estado
Aceptado

## Contexto
Se requiere una base reproducible para iniciar el proyecto Evidence as Code, evitando complejidad temprana.

## Decisión
Implementar primero una plataforma mínima en Docker Compose con:

- 2 apps Spring Boot con endpoint de salud.
- 2 PostgreSQL separados (app y metadata).
- variables en archivo .env externo.
- healthchecks y políticas de reinicio.

## Consecuencias

- Permite validar disponibilidad del stack en minutos.
- Reduce riesgo antes de integrar OpenTelemetry, OpenSCAP y pgBackRest.
- Facilita evolución incremental por fases.
