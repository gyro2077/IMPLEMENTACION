# RUNBOOK DE DEMOSTRACIÓN - FASE 6 (End-to-End)

Este documento es la guía oficial paso a paso para ejecutar la demostración final del proyecto `flisol-evidence-as-code` (Fase 6) para una audiencia técnica y no técnica.

## 1. Objetivo de la Demostración
Mostrar en vivo la trazabilidad de cumplimiento, respaldo de datos y simulación de recuperación ante desastres de un servidor, en un flujo automatizado (E2E) que debe completarse en **menos de 7 minutos**.

## 2. Pre-Requisitos y Entorno
Asegúrese de estar en la carpeta raíz del proyecto (`IMPLEMENTACION/`).
El stack de contenedores debe estar inicializado:
```bash
docker compose up -d
```
Verifique que los contenedores base estén arriba usando `docker ps`. Se requiere:
- `flisol-postgres-metadata`
- `flisol-postgres-app`
- `flisol-evidence-server`
- `flisol-target-app`
- Servicios de Telemetría (OpenTelemetry Collector y Jaeger)

## 3. Ejecución de la Demo (1 solo comando)
Hemos reducido los pasos manuales a **uno solo**. Ejecute desde la raíz del proyecto:

```bash
./automation/scripts/demo_run.sh
```

### ¿Qué hace internamente este script?
1. **Verificación:** Espera a que los contenedores HTTP estén operativos.
2. **Reset (`reset_demo.sh`):** Limpia datos previos de la demo en la base de datos de auditoría para garantizar que la demostración sea repetible.
3. **Dataset semilla (`seed_demo_data.sh`):** Inyecta reportes OpenSCAP base pre-calculados, simulando un escaneo de seguridad, e inyecta tráfico web para las trazas.
4. **Backup (`backup_db.sh`):** Desencadena un respaldo total (Full Backup) a la base de datos de producción (`flisol-postgres-app`) usando `pgBackRest`.
5. **Restore Simulado (`restore_db.sh`):** Restaura ese último backup en un contenedor aislado (`flisol-postgres-restore`) validando que el RTO (Recovery Time Objective) es bajo.
6. **Smoke Test (`smoke_test_restore.sh`):** Realiza comprobaciones sintéticas automatizadas al backup levantado para confirmar que los datos no están corruptos.
7. **Reporte y PDF:** Le pide a la API generar y descargar un PDF final con las evidencias en `reports/generated/`.

## 4. Validación Visual (Audiencia no técnica)
Después de ejecutar el script `demo_run.sh`, ingrese al dashboard mejorado:

**URL:** `http://localhost:8082/` (o el puerto configurado de `evidence-server`)

El dashboard ahora cuenta con:
- **Vista de Semáforo:** Tarjetas Glassmorphism y colores amigables (Verde, Amarillo, Rojo).
- **KPIs Claros:** Se ocultan UUIDs y se enfatiza en los conceptos: **Auditoría de Seguridad**, **Respaldo** y **Prueba de Recuperación**.
- **Resultados del Host Principal:** En la demostración, el servidor llamado `target-cachyos` debería lucir en **verde** mostrando el RTO en segundos.

## 5. Fallos Conocidos y Contingencia

1. **El contenedor de Restore falla en iniciar (`pgbackrest-restore-worker`)**
   - *Causa:* El host tiene poca memoria libre y el kernel de Linux mata el proceso del contenedor por OOM (Out Of Memory).
   - *Contingencia:* Ejecute `docker rm -f flisol-postgres-restore` y re-ejecute `./automation/scripts/reset_demo.sh --hard` para limpiar por completo. Luego inicie de nuevo.
2. **El reporte JasperReports falla (`java.lang.NoClassDefFoundError` o font error)**
   - *Causa:* Problema conocido en Alpine/Slim con librerías de fuentes (fontconfig).
   - *Contingencia:* El script generará un estado `FAILED`. La evidencia puede visualizarse mediante la API JSON y el Dashboard de forma directa omitiendo el PDF temporalmente. El entorno actual en Debian-based `eclipse-temurin` mitigó este bug en gran medida.
3. **El tiempo de demo sobrepasa los 7 minutos (420s)**
   - *Causa:* El Restore puede tardar más si el disco del host está lento.
   - *Contingencia:* El JSON final reportará `"target_under_7min": "fail"`. Muestre que la funcionalidad no está rota, solo sufre cuellos de botella de hardware de demo local.

## 6. Congelación de Versión
El proyecto actualmente se considera estable para la Demo. No se recomiendan cambios en arquitectura o dependencias (Spring Boot, versiones de PostgreSQL, OpenSCAP) durante las 48 horas previas a la presentación. Todas las pruebas de integración en `evidence-server` están en verde (MockMvc).

## 7. Apéndice: Medición de Tiempos y Logs
Al final del script `demo_run.sh`, se generará un log E2E en `reports/generated/demo-run-YYYYMMDD...json`. Contiene un desglose exacto (en segundos) de lo que demoró cada sub-paso, confirmando objetivamente el cumplimiento de las métricas de negocio.
