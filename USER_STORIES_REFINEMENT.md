# USER_STORIES_REFINEMENT.md — Worker Health Check

**Feature**: `001-worker-health-check`
**Fecha**: 2026-04-08
**Propósito**: Documentar el proceso de refinamiento de 3 Historias de Usuario aplicando criterios INVEST, comparando la versión original propuesta por IA versus la versión refinada por el desarrollador.

---

## Metodología aplicada

Para cada HU se muestra:
1. **Versión IA (raw)** — lo que propuso el LLM sin intervención
2. **Versión refinada (humano)** — después de aplicar criterio técnico y de negocio
3. **Tabla INVEST** — análisis de cada criterio
4. **Diferencias detectadas** — qué cambió y por qué

---

## HU-1: Consultar estado global del worker (US1 — Priority P1)

### Versión propuesta por IA (raw)

> *"Como desarrollador, quiero un endpoint de salud para saber si el servicio funciona."*

**Problemas detectados**:
- Actor incorrecto (`desarrollador` en lugar del consumidor real)
- Sin criterio de aceptación medible (¿qué significa "funciona"?)
- Sin restricción de tiempo de respuesta
- No menciona los estados posibles (UP/WARN/DOWN)
- No es testable de forma independiente

### Versión refinada (humano)

> *"Como ingeniero de operaciones o probe de Kubernetes, necesito consultar el estado consolidado del worker (UP / WARN / DOWN) en un único request HTTP en menos de 500 ms, sin necesidad de revisar logs ni acceder a la base de datos, para garantizar visibilidad operacional en tiempo real."*

### Tabla INVEST

| Criterio | Versión IA | Versión refinada | ¿Cumple? |
|----------|-----------|-----------------|---------|
| **I**ndependent | No — mezcla "funciona" sin separar componentes | Testeable de forma aislada: solo verifica el status global, no los detalles | ✅ |
| **N**egotiable | No hay contexto para negociar | Latencia (< 500 ms), actor (Kubernetes probe), estados explícitos | ✅ |
| **V**aluable | Poco claro para el negocio | Elimina la necesidad de acceso manual a logs o BD → reduce MTTD | ✅ |
| **E**stimable | Sin datos suficientes | 6 clases nuevas + 3 modificadas, estimado en plan.md | ✅ |
| **S**mall | No — demasiado vago y amplio | Acotado a status global solamente; componentes son US2 | ✅ |
| **T**estable | No hay escenarios | 4 escenarios de aceptación + test independiente con `curl` | ✅ |

### Diferencias clave

| # | Diferencia | Justificación técnica |
|---|-----------|----------------------|
| 1 | Actor cambia de "desarrollador" a "ingeniero de operaciones / Kubernetes probe" | Los probes no tienen sesión humana; el consumidor real define el contrato del endpoint |
| 2 | Se agrega SLA de < 500 ms | Sin esto el criterio es vacío: un endpoint que responde en 10 s es inútil para Kubernetes |
| 3 | Se explicitan los 3 estados (UP/WARN/DOWN) | La IA propuso un booleano implícito; el diseño real requiere un tri-estado para el canal de alertas |
| 4 | Se elimina "para saber si funciona" — se reemplaza por el valor de negocio | Saber **qué** status hay y **qué hacer** (alertar, reiniciar, ignorar) es el valor real |

---

## HU-2: Alerta por eventos fallidos con umbrales configurables (US3 — Priority P3)

### Versión propuesta por IA (raw)

> *"Como administrador, quiero ver cuántos eventos fallaron para decidir si hay un problema."*

**Problemas detectados**:
- No define cuándo es "problema" → sin threshold concreto
- Sin diferenciación WARN vs DOWN (solo implica un estado binario)
- Los umbrales están hardcodeados implícitamente
- No es verificable sin acceso a logs

### Versión refinada (humano)

> *"Como equipo de operaciones, necesito que el health endpoint transite automáticamente a WARN cuando `failedEvents >= 10` y a DOWN cuando `failedEvents >= 20`, con umbrales configurables en `application.properties` sin redespliegue, para que las herramientas de monitoring generen alertas antes de que los backlogs superen los SLAs de facturación."*

### Tabla INVEST

| Criterio | Versión IA | Versión refinada | ¿Cumple? |
|----------|-----------|-----------------|---------|
| **I**ndependent | No — depende de "ver" la UI | Testeable solo con BD + endpoint; sin UI | ✅ |
| **N**egotiable | Valores implícitos no negociables | `warnThreshold=10`, `downThreshold=20` son defaults negociables en config | ✅ |
| **V**aluable | Vago — "decidir si hay un problema" | Previene backlogs antes de superar SLAs de facturación (impacto de negocio concreto) | ✅ |
| **E**stimable | No | T002, T006, T015 en tasks.md — estimable | ✅ |
| **S**mall | Amplio e impreciso | Acotado a threshold evaluation en `OutboxHealthMetrics.compute()` | ✅ |
| **T**estable | No | 5 escenarios boundary value (9, 10, 19, 20, misconfiguration) | ✅ |

### Diferencias clave

| # | Diferencia | Justificación técnica |
|---|-----------|----------------------|
| 1 | La IA no propuso umbrales configurables | La IA hardcodeó el concepto de "muchos fallidos"; el desarrollador identificó que sin externalización el threshold es deuda técnica inmediata |
| 2 | Se diferencia WARN de DOWN | La IA propuso un único "hay problema"; el diseño real necesita dos niveles para dar ventana de reacción al on-call antes del DOWN crítico |
| 3 | Se agrega `>=` explícito | La IA usó "supera" (implica `>`); el correcto es `>=` (boundary inclusivo) — detectado en la revisión de edge cases |
| 4 | Se vincula al impacto de negocio (SLA de facturación) | La IA propuso valor técnico genérico; el refinamiento conecta el threshold con la consecuencia real para el negocio |

---

## HU-3: Detectar scheduler detenido (US4 — Priority P4)

### Versión propuesta por IA (raw)

> *"Como operador, quiero saber si el scheduler está funcionando para evitar pérdida de datos."*

**Problemas detectados**:
- Sin definición de "funcionando" vs "detenido"
- No menciona el timeout configurable
- Trata `null` como DOWN (falso positivo en fresh startup)
- "pérdida de datos" es impreciso — el Outbox Pattern garantiza que no hay pérdida, solo retraso

### Versión refinada (humano)

> *"Como ingeniero de operaciones o probe de Kubernetes, necesito que el health endpoint reporte DOWN cuando `lastProcessedAt` supera el timeout configurado (`schedulerTimeoutSeconds=30`), y que un `lastProcessedAt` null inmediatamente después de un reinicio sea informacional (UP, no DOWN), para detectar schedulers silenciosamente detenidos sin generar falsos positivos en restarts normales."*

### Tabla INVEST

| Criterio | Versión IA | Versión refinada | ¿Cumple? |
|----------|-----------|-----------------|---------|
| **I**ndependent | No | Testeable de forma aislada con mock de `SchedulerStatePort` | ✅ |
| **N**egotiable | Sin parámetro de tiempo | `schedulerTimeoutSeconds=30` configurable en properties | ✅ |
| **V**aluable | Impreciso ("pérdida de datos") | Detecta fallo silencioso sin acumulación visible de errores → valor directo para billing | ✅ |
| **E**stimable | No | T007, T008, T016 en tasks.md | ✅ |
| **S**mall | Demasiado vago | Acotado: solo evaluación de timeout en `compute()` + `SchedulerTracker` | ✅ |
| **T**estable | No | 4 escenarios: stalled > 30s, within timeout, null/fresh start, stall wins over low failedEvents | ✅ |

### Diferencias clave

| # | Diferencia | Justificación técnica |
|---|-----------|----------------------|
| 1 | La IA trató `null` como DOWN | Error crítico de diseño — generaría alerta falsa en cada restart de Kubernetes; el desarrollador identificó que `null` = "no ha corrido aún" ≠ "está detenido" |
| 2 | La IA no propuso `SchedulerStatePort` | El desarrollador identificó que inyectar `SchedulerTracker` directamente en `OutboxHealthMetrics` viola Hexagonal (application no puede importar infrastructure) — se introdujo un puerto de output |
| 3 | Se agrega la regla de prioridad de estados (DOWN scheduler > WARN failedEvents) | La IA no consideró qué pasa cuando ambas condiciones coexisten; se definió explícitamente que DOWN siempre gana |
| 4 | "pérdida de datos" → "detección de fallo silencioso" | El Outbox Pattern garantiza durabilidad; lo que falla es el procesamiento, no el almacenamiento — el lenguaje importa en los criterios de aceptación |

---

## Resumen de ajustes HITL

| HU | Correcciones principales del desarrollador | Tipo de corrección |
|----|------------------------------------------|-------------------|
| US1 | Actor real, SLA medible, tri-estado explícito | Precisión del valor de negocio |
| US3 | Thresholds configurables, boundary `>=`, diferenciación WARN/DOWN | Decisión técnica de diseño |
| US4 | `null ≠ DOWN`, `SchedulerStatePort` nuevo, regla de prioridad | Corrección de violación arquitectónica |
