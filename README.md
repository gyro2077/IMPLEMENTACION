# flisol-evidence-as-code

Base inicial del proyecto Evidence as Code para FLISoL.

## Alcance de esta entrega (Fase 1)

- Estructura de repositorio inicial.
- Dos aplicaciones Spring Boot mínimas:
  - target-app
  - evidence-server
- Dos PostgreSQL separados:
  - app DB
  - metadata DB
- Docker Compose funcional con healthchecks y reinicio automático.
- Configuración por variables externas usando archivo .env.

## Requisitos

- Docker Engine
- Docker Compose plugin

## Arranque rápido

1. Copia el archivo de ejemplo:

cp compose/.env.example compose/.env

2. Levanta todo:

docker compose --env-file compose/.env -f compose/docker-compose.yml up -d --build

3. Verifica salud:

curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health

## Apagar

docker compose --env-file compose/.env -f compose/docker-compose.yml down

## Notas

- Esta fase solo prepara fundaciones y alcance.
- Los componentes de observabilidad/compliance/backup se agregan en fases posteriores.

**Solo está implementada la Fase 1.**

Esta fase construye la base técnica mínima para el proyecto:

- Estructura inicial del repositorio.
- Dos aplicaciones Spring Boot mínimas:
  - `target-app`
  - `evidence-server`
- Dos bases PostgreSQL separadas:
  - `postgres-app`
  - `postgres-metadata`
- Docker Compose funcional.
- Variables externas mediante archivo `.env`.
- Healthchecks y validación de arranque.

## Arquitectura actual

```text
usuario
  ├──> target-app (puerto 8081)
  │       └──> postgres-app (puerto 5433)
  │
  └──> evidence-server (puerto 8082)
          └──> postgres-metadata (puerto 5434)