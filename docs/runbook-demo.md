# Runbook de demo (borrador)

Este documento se completa en fases 5 y 6.

Pasos mínimos actuales:

1. docker compose --env-file compose/.env -f compose/docker-compose.yml up -d --build
2. curl http://localhost:8081/actuator/health
3. curl http://localhost:8082/actuator/health
