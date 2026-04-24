# Plan maestro de evolución — Dashboard administrador y reportes premium
## Proyecto: Evidence as Code para Linux
**Fecha:** 2026-04-23  
**Estado del proyecto:** MVP funcional validado, con siguiente foco en UX/UI operable y reportes de alto impacto.

---

# 1. Propósito de este documento

Este documento sirve como **bitácora técnica y funcional** de la siguiente etapa del proyecto, para evitar perder el rumbo al implementar mejoras en:

1. **Dashboard administrador unificado**
2. **Reportes de evidencia más visuales, completos y ejecutivos**
3. **Mejor narrativa para audiencia no técnica**
4. **Operación del sistema desde interfaz, no solo desde consola**

La idea de esta fase no es rehacer el sistema, sino **convertir un MVP técnico funcional en una plataforma demostrable, entendible y operable**.

---

# 2. Estado actual del proyecto

Actualmente el sistema ya demuestra capacidades reales en estas áreas:

## 2.1 Observabilidad
- `target-app` expone trazas y métricas.
- Jaeger permite ver trazas.
- Prometheus scrapea métricas de `target-app`.
- Loki + Alloy ya reciben logs.
- Grafana ya tiene datasources y dashboard técnico base.

## 2.2 Cumplimiento
- Se puede importar y consultar un scan de compliance.
- El sistema guarda:
  - score
  - profile_id
  - reglas fallidas
  - rutas a ARF/XML y HTML

## 2.3 Recuperación
- Se ejecuta backup full con pgBackRest.
- Se ejecuta restore en entorno aislado.
- Se corren smoke tests.
- Se mide RTO.
- Se persiste evidencia.

## 2.4 Consolidación
- `evidence-server` expone APIs de:
  - hosts
  - scans
  - backups
  - restores
  - overview
  - report jobs
- Existe dashboard HTML simple.
- Existe generación de PDF real.

## 2.5 Demo
- `demo_run.sh` automatiza el flujo de punta a punta.
- La demo ya corre en menos de 7 minutos.
- Se genera evidencia y PDF automáticamente.

---

# 3. Problema de la siguiente etapa

Aunque el sistema ya funciona, todavía hay una brecha entre:

## 3.1 Lo técnico
Hoy el sistema es fuerte técnicamente, pero requiere:
- conocer endpoints
- usar Bash
- usar `curl`
- entender `jq`
- navegar entre varias herramientas

## 3.2 Lo demostrable
Para una audiencia, un usuario final o una persona no técnica, todavía no es ideal depender de:
- consola
- scripts
- APIs crudas
- herramientas separadas

## 3.3 Lo que se quiere lograr
Se quiere que el sistema pueda presentarse como:

> una plataforma de software libre que permite observar, evaluar, respaldar, restaurar y evidenciar un servicio Linux desde una experiencia visual clara y usable.

---

# 4. Visión de la siguiente fase

La visión de esta etapa es convertir el sistema en una:

# **Consola Administrativa de Evidencia Operativa**

Esta consola será el **centro de control del proyecto**, y tendrá como objetivo:

- mostrar el estado del sistema
- explicar qué hace cada módulo
- permitir ejecutar acciones
- mostrar resultados en tiempo real
- servir de puente hacia Grafana, Jaeger y PDFs
- reducir al mínimo la dependencia de consola para la demo

---

# 5. Objetivos de esta nueva etapa

## Objetivo principal
Convertir el dashboard actual en un **panel administrador** usable incluso por alguien no técnico, y mejorar el PDF para que luzca como un reporte de auditoría profesional.

## Objetivos específicos
1. Convertir el dashboard en la **puerta principal del sistema**.
2. Permitir que el usuario ejecute acciones clave desde la UI.
3. Traducir señales técnicas a lenguaje entendible.
4. Mejorar el valor visual y ejecutivo del PDF.
5. Preparar reportes comparativos tipo “antes / después”.
6. Mantener intacto el motor actual del proyecto.

---

# 6. Principios de diseño

## 6.1 No romper lo que ya funciona
Todo lo nuevo debe apoyarse sobre el sistema actual:
- scripts
- evidence-server
- reportes
- Prometheus/Grafana/Loki/Jaeger
- backup/restore
- dashboard existente

## 6.2 La UI no reemplaza la consola, la abstrae
La consola, APIs y scripts seguirán existiendo como capa experta.  
El dashboard será la capa amigable.

## 6.3 El usuario no debe necesitar Bash
La meta de UX es que una persona pueda:
- entender el estado,
- hacer acciones básicas,
- y revisar evidencia,
sin depender de terminal.

## 6.4 Cada acción debe dejar evidencia
Toda acción disparada desde UI debe:
- crear job
- cambiar estado
- guardar resultado
- mostrar feedback al usuario

## 6.5 Todo debe contar una historia
El dashboard no solo debe mostrar datos, sino responder:
- qué pasó,
- qué significa,
- qué riesgo hay,
- qué decisión tomar.

---

# 7. Qué se va a construir

---

# 7.1 Módulo A — Dashboard Administrador Unificado

## Objetivo
Que el dashboard deje de ser solo informativo y se convierta en un **centro de operación**.

## Qué debe incluir

### A. Resumen ejecutivo
Tarjetas visibles con:
- estado global del entorno
- host principal
- score de seguridad
- último backup
- último restore
- RTO
- PDF más reciente

### B. Estado visual por host
Cada host debe mostrar:
- semáforo
- score
- backup
- restore
- observaciones
- fecha de última evidencia

### C. Wizard o flujo guiado de demo
Bloques visuales tipo pasos:
1. Preparar entorno
2. Ejecutar auditoría
3. Crear backup
4. Simular incidente
5. Restaurar
6. Validar
7. Generar reporte

### D. Botones de acción
Botones administrables desde UI:
- Ejecutar scan
- Crear backup
- Ejecutar restore
- Generar PDF
- Recargar dataset demo
- Abrir Grafana
- Abrir Jaeger
- Ver logs

### E. Timeline de evidencias
Vista tipo línea de tiempo:
- scan completado
- backup creado
- restore ejecutado
- smoke test ejecutado
- reporte generado

### F. Vista de observabilidad simplificada
Módulo con:
- métricas clave
- logs recientes
- trazas o enlaces
- accesos rápidos a Grafana y Jaeger

### G. Centro de reportes
Lista de:
- reportes generados
- fecha
- host
- estado
- descarga
- previsualización

---

# 7.2 Módulo B — Reportes PDF premium

## Objetivo
Rediseñar el PDF actual para que no parezca un export básico, sino un **reporte de auditoría ejecutiva y técnica**.

## Qué debe mejorar
- jerarquía visual
- estilos
- portada
- secciones claras
- tablas más bonitas
- colores por severidad
- gráficos
- narrativa ejecutiva
- mayor utilidad para presentación

## Estructura objetivo del PDF

### Página 1 — Portada
- título del reporte
- host
- fecha
- estado general
- score
- backup/restore
- subtítulo

### Página 2 — Resumen ejecutivo
- score
- estado
- hallazgos críticos
- backup
- restore
- RTO
- tabla resumen de evidencia

### Página 3 — Cumplimiento
- score
- perfil
- top hallazgos
- tabla por severidad
- recomendación breve

### Página 4 — Resiliencia
- backup label
- duración
- tamaño
- restore
- RTO
- smoke tests
- filas recuperadas
- timeline backup → restore → validación

### Página 5 — Observabilidad
- servicios monitoreados
- trazas disponibles
- logs disponibles
- métricas disponibles
- referencia real a Jaeger/Grafana

### Página 6 — Conclusión y recomendaciones
- estado final
- riesgos
- recomendaciones
- próximos pasos

---

# 7.3 Módulo C — Reportes comparativos

## Objetivo
Agregar valor analítico, no solo evidencia estática.

## Tipos de comparación deseados

### A. Antes / después de cumplimiento
Comparar:
- score inicial
- score final
- hallazgos críticos iniciales
- hallazgos críticos finales

### B. Antes del incidente / después del restore
Comparar:
- estado inicial
- datos esperados
- datos restaurados
- RTO
- smoke tests

### C. Resumen histórico
Comparar varias corridas:
- últimos backups
- últimos restores
- últimos reportes
- tendencia del score

## Importante
La frase correcta no es “antes y después del backup”, porque el backup no mejora el sistema.  
Las comparaciones correctas son:
- **antes / después de hardening**
- **antes del incidente / después del restore**
- **histórico de evidencia**

---

# 8. Qué no se va a hacer ahora

Para no romper el proyecto ni expandir demasiado el alcance, esta etapa **NO** incluye:

- autenticación compleja
- control multiusuario
- RBAC avanzado
- Kubernetes
- frontend SPA grande
- refactor total del backend
- reemplazar scripts Bash por orquestador enterprise
- colas complejas
- despliegue distribuido multi-host productivo

---

# 9. Arquitectura propuesta para esta etapa

## 9.1 El dashboard como centro
La idea es que el dashboard sea la cara visible.

### El usuario verá:
- estado
- decisiones
- botones
- reportes
- métricas
- logs
- trazas

### Pero por detrás seguirá funcionando:
- `evidence-server`
- scripts
- APIs
- Prometheus
- Grafana
- Loki
- Jaeger
- Jasper

## 9.2 Modelo de operación
### UI
envía una acción

### Backend
convierte la acción en job

### Job
ejecuta script / servicio / integración

### Backend
guarda resultado y devuelve estado

### UI
actualiza timeline, estado y evidencia

---

# 10. Propuesta de UX/UI

## 10.1 Meta UX
Si un niño o una persona no técnica entra, debe poder responder:
- ¿qué hace este sistema?
- ¿cómo está el host?
- ¿qué botón debo usar?
- ¿ya se hizo backup?
- ¿ya se recuperó?
- ¿ya hay reporte?
- ¿dónde veo más detalle?

## 10.2 Reglas UX
- lenguaje humano
- texto corto
- colores claros
- evitar UUIDs visibles
- explicaciones contextuales
- feedback inmediato
- iconografía simple
- pasos guiados

## 10.3 Estados sugeridos
- **Listo**
- **Validado con observaciones**
- **Parcial**
- **Problema**
- **Sin evidencia**

## 10.4 Paleta y estilo
Se mantiene:
- verde
- amarillo
- rojo
- gris

Pero con:
- mayor jerarquía visual
- tarjetas más claras
- bloques de acción
- timeline
- paneles con iconos
- tablas más limpias

---

# 11. Plan de implementación recomendado

Se recomienda hacer esto en **dos etapas grandes** y una opcional.

---

## ETAPA 1 — Dashboard Administrador
### Objetivo
Volver el dashboard usable, operable y demostrable.

### Tareas
1. Rediseñar home ejecutivo
2. Crear módulos de estado
3. Agregar acciones con botones
4. Crear vista de timeline
5. Crear vista de reportes
6. Crear vista de observabilidad simplificada
7. Integrar accesos a Grafana y Jaeger
8. Agregar feedback visual de jobs

### Entregable
Un dashboard desde donde puedas:
- ver el sistema
- tomar decisiones
- ejecutar acciones clave
- revisar resultados

---

## ETAPA 2 — PDF premium
### Objetivo
Volver el reporte final más ejecutivo, visual y profesional.

### Tareas
1. Rediseñar JRXML
2. Mejorar portada
3. Agregar tablas bonitas
4. Agregar gráficos
5. Agregar timeline
6. Agregar secciones de observabilidad
7. Agregar comparación cuando haya datasets comparables

### Entregable
Un PDF que sirva para:
- evidencia
- presentación
- auditoría
- entrega formal

---

## ETAPA 3 (Opcional) — Reportes comparativos
### Objetivo
Agregar valor analítico e histórico.

### Tareas
1. Comparación antes/después de compliance
2. Comparación backup / incidente / restore
3. Tendencias por host
4. Diferencias por corrida

---

# 12. Recomendación técnica de backend

## Para acciones desde dashboard
No ejecutar scripts directamente desde el frontend.

### Correcto:
- frontend llama endpoint
- backend crea job
- backend ejecuta runner
- backend persiste resultado
- frontend consulta progreso

## Acciones candidatas
- `POST /api/actions/seed-demo`
- `POST /api/actions/run-compliance/{host}`
- `POST /api/actions/backup/{host}`
- `POST /api/actions/restore/{host}`
- `POST /api/actions/report/{host}`

## Lecturas candidatas
- `GET /api/jobs`
- `GET /api/reports`
- `GET /api/logs/recent`
- `GET /api/observability/summary`
- `GET /api/hosts/{host}/timeline`

---

# 13. Recomendación técnica de frontend/dashboard

## Sin reescribir todo
Se puede evolucionar el dashboard actual sin romperlo.

### Opción recomendada
Mantener `evidence-server` como generador del dashboard HTML, pero enriquecerlo con:
- secciones nuevas
- más UX
- paneles interactivos
- botones
- refresh
- tarjetas de progreso

### Opción futura
Si más adelante quieres algo más complejo, ahí sí considerar:
- frontend más dinámico
- HTMX / Alpine / React
Pero **no es necesario todavía**.

---

# 14. Riesgos y mitigación

## Riesgo 1 — Querer meter demasiado en una sola iteración
**Mitigación:** dividir en dashboard primero, PDF después.

## Riesgo 2 — Romper la demo actual
**Mitigación:** no tocar `demo_run.sh` salvo necesidad extrema; agregar pruebas.

## Riesgo 3 — Mezclar backend y lógica de jobs sin control
**Mitigación:** introducir endpoints de acciones con persistencia clara.

## Riesgo 4 — Hacer un dashboard bonito pero poco útil
**Mitigación:** diseñarlo a partir de decisiones concretas que el usuario debe tomar.

## Riesgo 5 — Hacer un PDF bonito pero vacío
**Mitigación:** cada bloque visual debe salir de datos reales del sistema.

---

# 15. Criterios de aceptación propuestos

## Dashboard
- una persona no técnica entiende qué hace el sistema
- puede ver el estado general del host
- puede identificar si el entorno está listo
- puede disparar acciones básicas sin consola
- puede descargar el reporte final
- puede navegar a observabilidad avanzada si quiere

## Reporte PDF
- tiene estructura ejecutiva
- usa datos reales
- se ve profesional
- muestra compliance, backup, restore y observabilidad
- sirve como evidencia formal

## Reportes comparativos
- permiten mostrar mejora real
- no dependen de texto fijo
- tienen narrativa clara

---

# 16. Qué se debe construir primero

## Recomendación final
Primero construir:

### 1. Dashboard administrador
Porque desbloquea:
- experiencia de usuario
- operación sin consola
- narrativa de demo

### 2. Reporte PDF premium
Porque mejora:
- percepción de valor
- calidad de evidencia
- impacto visual

### 3. Reportes comparativos
Porque agregan:
- análisis
- evolución
- storytelling técnico

---

# 17. Resultado esperado al terminar esta etapa

Cuando esto esté implementado, el proyecto ya no se sentirá solo como un laboratorio técnico.

Se sentirá como una:

# **Plataforma libre de auditoría operativa para Linux**

donde alguien podrá:

- entrar al panel
- entender qué está pasando
- disparar acciones
- revisar evidencia
- ver métricas y logs
- abrir trazas
- descargar reportes profesionales

---

# 18. Frase guía del proyecto después de esta evolución

> “No solo monitoreamos un servicio. Construimos una consola libre que demuestra trazabilidad, cumplimiento y recuperación de un sistema Linux, y convierte esa evidencia en reportes visuales y operables.”

---

# 19. Próximo paso recomendado

El siguiente paso práctico es:

## **Diseñar e implementar primero el Dashboard Administrador**
y después:
## **Rediseñar el PDF premium**

No mezclar ambas cosas en una sola iteración si quieres mantener control del proyecto.

---

# 20. Nota final

Este documento no sustituye el manual técnico original.  
Este documento sirve como **plan de evolución del producto** sobre un sistema ya funcional.

La arquitectura base actual se conserva.  
Lo que cambia es:
- la experiencia de uso,
- la capacidad operativa del panel,
- y el nivel de presentación de la evidencia.
