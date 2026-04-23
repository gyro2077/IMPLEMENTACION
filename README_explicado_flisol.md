# README explicado — flisol-evidence-as-code

## ¿Qué es este sistema?

Este proyecto es una **plataforma de evidencia operativa** para Linux.

Su idea central es demostrar, con datos y artefactos verificables, tres capacidades:

1. **Observabilidad**: ver qué pasa en una petición y seguir su rastro.
2. **Cumplimiento**: evaluar seguridad básica del host con OpenSCAP.
3. **Recuperación**: demostrar que PostgreSQL se puede respaldar y restaurar de verdad.

En lugar de mostrar herramientas sueltas, el sistema junta todo en una sola historia:

- una app objetivo,
- un servidor central de evidencia,
- APIs de consulta,
- un dashboard,
- y un PDF final.

---

## Qué componentes tiene

### 1. target-app
Es la aplicación objetivo.

Sirve para:
- responder peticiones,
- generar tráfico trazable,
- hablar con PostgreSQL,
- ser el sistema “bajo prueba”.

### 2. postgres-app
Es la base de datos de la app objetivo.

Sirve para:
- guardar los datos funcionales,
- ser respaldada por pgBackRest,
- ser restaurada en la fase de recuperación.

### 3. evidence-server
Es la aplicación central del proyecto.

Sirve para:
- guardar evidencia de compliance,
- guardar metadata de backups y restores,
- exponer APIs de lectura,
- mostrar el dashboard,
- generar el PDF final.

### 4. postgres-metadata
Es la base de datos del servidor de evidencia.

Sirve para guardar:
- scans de compliance,
- reglas fallidas,
- backups,
- restores,
- jobs de reportes.

### 5. OpenTelemetry + Collector + Jaeger
Sirven para observabilidad.

Flujo:
- target-app genera trazas,
- el Collector las recibe,
- Jaeger las muestra.

### 6. OpenSCAP
Sirve para compliance.

Flujo:
- se ejecuta un scan,
- genera ARF/XML y HTML,
- el parser resume score y hallazgos,
- evidence-server los guarda.

### 7. pgBackRest
Sirve para recuperación.

Flujo:
- crea backup,
- restaura en un entorno aislado,
- se ejecutan smoke tests,
- se guarda el RTO y el resultado.

### 8. JasperReports
Sirve para el PDF.

Flujo:
- evidence-server junta datos reales,
- genera un PDF por host,
- el PDF se puede descargar por API.

---

## Qué hace el sistema hoy

### Observabilidad
- target-app emite trazas.
- Jaeger puede mostrar una petición y su recorrido.

### Cumplimiento
- evidence-server puede listar scans.
- cada scan guarda score, artefactos y hallazgos.

### Recuperación
- hay backups reales.
- hay restores verificados.
- hay smoke tests con conteo y validación.

### Consolidación
- `/api/hosts`
- `/api/compliance/scans`
- `/api/backups`
- `/api/restores`
- `/api/evidence/overview`
- `/dashboard`

### Reporte final
- `POST /api/reports/evidence/{host}` genera un PDF real.
- `GET /api/reports/{id}` devuelve estado del job.
- `GET /api/reports/{id}/download` descarga el PDF.

---

## Qué demuestra cada fase

### Fase 1 — Fundaciones
Se construyó la base reproducible:
- Docker Compose,
- dos apps,
- dos PostgreSQL,
- healthchecks.

### Fase 2 — Observabilidad
Se logró:
- trazas automáticas,
- Collector,
- Jaeger,
- endpoint observable.

### Fase 3 — Compliance
Se logró:
- scan OpenSCAP,
- parser,
- score y hallazgos persistidos,
- endpoints de consulta.

### Fase 4 — Recuperación
Se logró:
- backup full,
- restore aislado,
- smoke tests,
- RTO medido,
- evidencia persistida.

### Fase 5 — Evidence Server y reportes
Se logró:
- APIs unificadas,
- dashboard,
- agregados por host,
- PDF real con Jasper.

### Fase 6 — End-to-end y demo
Se logró:
- dataset demo,
- reset reproducible,
- script de demo de un solo comando,
- medición de tiempos,
- runbook de presentación.

---

## Flujo completo del sistema

1. Se levanta el stack.
2. Se carga dataset demo.
3. target-app genera tráfico.
4. OpenTelemetry/Jaeger muestran trazas.
5. Se registra compliance.
6. Se hace backup de PostgreSQL.
7. Se simula incidente.
8. Se restaura en contenedor aislado.
9. Se corren smoke tests.
10. evidence-server consolida todo.
11. Se muestra dashboard.
12. Se genera PDF final.

---

## Qué significa el dashboard

El dashboard actual es una vista administrativa simple pensada para demo.

Muestra:
- hosts,
- estado visual,
- cumplimiento,
- backups,
- restores,
- enlaces a APIs crudas.

### Estados visuales
- **Verde**: listo
- **Amarillo**: parcial
- **Rojo**: problema
- **Gris**: sin evidencia

### Host principal de demo
El host destacado es `target-cachyos`.

Ahí se resume:
- score de cumplimiento,
- último backup,
- último restore,
- estado general.

---

## Qué NO hace todavía el dashboard

Todavía no hace estas cosas directamente desde la UI:
- lanzar backup con un botón,
- lanzar restore con un botón,
- lanzar reporte PDF con un botón,
- mostrar logs en panel embebido,
- mostrar trazas de Jaeger embebidas,
- mostrar respuestas JSON bonitas dentro del dashboard.

Hoy el dashboard es principalmente:
- **vista ejecutiva / administrativa**,
- con enlaces a APIs crudas,
- pero no una consola operativa completa.

---

## Qué sería una mejora natural

Una mejora muy buena sería convertir el dashboard en un **panel administrativo de demo**.

### Posibles mejoras
1. Botón **Cargar dataset demo**
2. Botón **Ejecutar backup**
3. Botón **Ejecutar restore**
4. Botón **Generar PDF**
5. Tarjeta de **último reporte PDF**
6. Sección **Logs recientes**
7. Sección **Trazas relevantes**
8. Vista bonita de JSON/API en tarjetas colapsables
9. Semáforos y mensajes orientados a público no técnico

Eso no cambia la arquitectura; solo mejora la presentación y la operación de demo.

---

## Cómo correrlo manualmente

### Levantar todo
```bash
cd compose
docker compose --env-file .env -f docker-compose.yml up -d --build
```

### Probar health
```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

### Ver dashboard
```bash
xdg-open http://localhost:8082/
```

### Probar APIs clave
```bash
curl http://localhost:8082/api/hosts
curl http://localhost:8082/api/compliance/scans
curl http://localhost:8082/api/backups
curl http://localhost:8082/api/restores
curl http://localhost:8082/api/evidence/overview
```

### Generar reporte PDF manualmente
```bash
curl -X POST http://localhost:8082/api/reports/evidence/target-cachyos
```

---

## Cómo correr la demo completa

```bash
./automation/scripts/demo_run.sh
```

Ese script hace:
- stack-check,
- reset-demo,
- seed-demo-data,
- backup,
- restore,
- smoke test,
- generate-report,
- resumen final.

---

## Qué deja la demo al final

En `reports/generated/` deja:
- resumen `.json`,
- resumen `.md`,
- PDF generado.

---

## Qué decir en una presentación

Una forma simple de explicarlo sería:

> Este sistema reúne evidencia operativa de un host Linux. No solo monitorea un servicio: demuestra trazabilidad de peticiones, evaluación de cumplimiento de seguridad, respaldo y restauración verificados, y consolida todo en un dashboard y un PDF final.

---

## Estado actual honesto

### Sí está logrado
- observabilidad básica,
- compliance persistido,
- backup y restore verificados,
- evidence-server,
- dashboard,
- PDF,
- demo en un solo script.

### Mejoras posibles
- dashboard más operativo e interactivo,
- logs y trazas embebidos,
- botones para ejecutar acciones desde la UI,
- documentación más pedagógica para usuarios no técnicos,
- freeze/versionado explícito antes del evento.

---

## Idea central del proyecto

Este proyecto no se trata de “tener muchas herramientas”.
Se trata de poder decir, con evidencia:

- **puedo ver lo que pasa**,
- **puedo medir seguridad básica**,
- **puedo recuperar el sistema**,
- **y puedo demostrarlo con un reporte final**.
