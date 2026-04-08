# TEST_PLAN.md — Worker Health Check

**Sistema**: foodtech_worker
**Feature**: 001-worker-health-check
**Versión**: 1.0.0
**Fecha**: 2026-04-08
**Autor**: Edison Reinoso
**Stack**: Java 25 · Spring Boot 3.5.11 · JUnit 5 · Mockito · JaCoCo 0.8.13

---

## 1. Alcance y Objetivos

### Objetivo
Validar que el endpoint `GET /actuator/health` refleje correctamente el estado del worker
(UP / WARN / DOWN) cubriendo las 4 historias de usuario definidas en `spec.md`.

### En alcance
- Estado global del worker (US1)
- Detalle por componente: db, rabbit, workerOutbox (US2)
- Alertas por failed events con boundary values (US3)
- Detección de scheduler detenido (US4)
- Respuesta ante DB caída (edge case)
- Comportamiento en fresh startup / null lastProcessedAt

### Fuera de alcance
- Autenticación del endpoint
- Interfaz de usuario (feature es backend-only)
- Persistencia del estado de salud en base de datos

---

## 2. Test Suites

| Suite | Herramienta | Tipo | Tests | Ejecutar con |
|-------|-------------|------|-------|-------------|
| **TS-01** Unit Suite | JUnit 5 + Mockito | Caja Blanca | 24 | `./gradlew test` |
| **TS-02** Integration Suite | Spring Boot Test | Componente | 3 | `./gradlew test -PincludeIntegration` |
| **TS-03** Black-Box Suite | Serenity BDD + REST Assured | Caja Negra | **10 (CP-01 a CP-10)** | `AUTO_API_SCREENPLAY` |

---

## 3. Test Cases — TS-01: Unit Suite (Caja Blanca)

> Fuente: tests existentes en `src/test/java/`
> Sin Spring context. Sin BD. Sin red. Solo lógica pura con Mockito.

### OutboxHealthMetricsTest — 12 tests

| ID | Método de test | US | Técnica de diseño | Tipo | Estado |
|----|----------------|----|-------------------|------|--------|
| TC-U01 | `compute_returnsUpWithMetrics_whenAllHealthy` | US2 | Flujo feliz | Verificar | ✅ PASS |
| TC-U02 | `compute_nullLastProcessedAt_onFreshStartup` | US4 | Valor nulo | Validar | ✅ PASS |
| TC-U03 | `compute_returnsDown_whenRepositoryThrows` | US2 | Manejo excepción | Validar | ✅ PASS |
| TC-U04 | `compute_returnsWarn_whenFailedEventsEqualsWarnThreshold` | US3 | **Boundary (=10)** | Validar | ✅ PASS |
| TC-U05 | `compute_returnsDown_whenFailedEventsEqualsDownThreshold` | US3 | **Boundary (=20)** | Validar | ✅ PASS |
| TC-U06 | `compute_returnsWarn_whenFailedEventsOneBeforeDownThreshold` | US3 | **Boundary (=19)** | Validar | ✅ PASS |
| TC-U07 | `compute_returnsUp_whenFailedEventsBelowWarnThreshold` | US3 | **Boundary (=9)** | Validar | ✅ PASS |
| TC-U08 | `compute_downWins_whenMisconfiguredThresholdsAreEqual` | US3 | Misconfiguration | Validar | ✅ PASS |
| TC-U09 | `compute_returnsDown_whenSchedulerStalled` | US4 | **Boundary (>30s)** | Validar | ✅ PASS |
| TC-U10 | `compute_returnsUp_whenSchedulerWithinTimeout` | US4 | **Boundary (<30s)** | Validar | ✅ PASS |
| TC-U11 | `compute_returnsUp_whenLastProcessedAtIsNull` | US4 | Valor nulo | Validar | ✅ PASS |
| TC-U12 | `compute_returnsDown_schedulerStallWins_overLowFailedEvents` | US4 | Prioridad de estados | Validar | ✅ PASS |

### WorkerHealthIndicatorTest — 5 tests

| ID | Método de test | US | Técnica | Tipo | Estado |
|----|----------------|----|---------|------|--------|
| TC-U13 | `health_returnsUp_whenSnapshotIsUp` | US1 | Traducción de estado | Verificar | ✅ PASS |
| TC-U14 | `health_returnsWarn_whenSnapshotIsWarn` | US1 | Traducción de estado | Verificar | ✅ PASS |
| TC-U15 | `health_returnsDown_whenSnapshotIsDown` | US1 | Traducción de estado | Verificar | ✅ PASS |
| TC-U16 | `health_includesReason_whenReasonIsNotNull` | US2 | Detalle condicional | Verificar | ✅ PASS |
| TC-U17 | `health_includesAllMetricDetails_always` | US2 | Contrato de respuesta | Verificar | ✅ PASS |

### SchedulerTrackerTest — 3 tests

| ID | Método de test | US | Técnica | Tipo | Estado |
|----|----------------|----|---------|------|--------|
| TC-U18 | `getLastProcessedAt_returnsEmpty_beforeFirstCall` | US4 | Estado inicial | Verificar | ✅ PASS |
| TC-U19 | `recordExecution_setsLastProcessedAt` | US4 | Comportamiento básico | Verificar | ✅ PASS |
| TC-U20 | `recordExecution_calledTwice_returnsLatestTimestamp` | US4 | Actualización de estado | Verificar | ✅ PASS |

### Tests heredados — 4 tests

| ID | Clase | Tipo | Estado |
|----|-------|------|--------|
| TC-U21–22 | `ProcessEventUseCaseTest` | Verificar | ✅ PASS |
| TC-U23 | `RabbitMqEventPublisherTest` | Verificar | ✅ PASS |
| TC-U24 | `TestEventControllerTest` | Verificar | ✅ PASS |

---

## 4. Test Cases — TS-03: Black-Box Suite (Caja Negra)

> Fuente: contratos definidos en `specs/001-worker-health-check/contracts/health-endpoint.md`
> No se conoce ni se accede al código fuente. Solo se usa el contrato HTTP.

| ID | Escenario | Precondición | Entrada | Resultado esperado | HTTP |
|----|-----------|-------------|---------|-------------------|------|
| CP-01 | Worker completamente sano | BD y Rabbit UP, 0 failed events | `GET /actuator/health` | `status: UP`, 3 componentes presentes, `workerOutbox.details` incluye `pendingEvents`, `failedEvents`, `lastProcessedAt` con `failedEvents: 0` | 200 |
| CP-02 | WARN por failed events en umbral | 10 eventos FAILED en BD (= warnThreshold) | `GET /actuator/health` | `status: WARN`, `components.workerOutbox.status: WARN`, reason menciona threshold | 200 |
| CP-03 | DOWN por failed events críticos | 20 eventos FAILED en BD (= downThreshold) | `GET /actuator/health` | `status: DOWN`, `components.workerOutbox.status: DOWN`, reason menciona threshold crítico | 503 |
| CP-04 | DOWN por scheduler detenido | Scheduler sin ejecutar > 30s | `GET /actuator/health` | `status: DOWN`, reason contiene "Scheduler stalled" y segundos transcurridos | 503 |
| CP-05 | DB caída — componente db DOWN | Contenedor Postgres detenido | `GET /actuator/health` | `status: DOWN`, `components.db.status: DOWN`, workerOutbox puede seguir UP o WARN | 503 |
| CP-06 | RabbitMQ caído — componente rabbit DOWN | Contenedor RabbitMQ detenido | `GET /actuator/health` | `status: DOWN`, `components.rabbit.status: DOWN` | 503 |
| CP-07 | Estructura de respuesta siempre completa | Worker en cualquier estado | `GET /actuator/health` | Body SIEMPRE incluye `components.workerOutbox.details` con los 3 keys: `pendingEvents`, `failedEvents`, `lastProcessedAt` | cualquiera |
| CP-08 | Tiempo de respuesta bajo carga normal | Worker sano, carga operacional normal | `GET /actuator/health` | Respuesta completa en **< 500 ms** (medición con `time_total` de curl o similar) | 200 |
| CP-09 | Independencia de componentes | BD caída, Rabbit caído, outbox sano | `GET /actuator/health` | `workerOutbox` mantiene su estado independientemente del estado de `db` y `rabbit` | 503 |
| CP-10 | Fresh startup — null lastProcessedAt | Worker recién iniciado sin procesar eventos | `GET /actuator/health` | `status: UP`, `lastProcessedAt: null`, reason "No events processed since last startup" — **no DOWN** | 200 |

---

## 5. Los 7 Principios del Testing aplicados

| # | Principio | Cómo se aplica en este proyecto |
|---|-----------|--------------------------------|
| **1** | Las pruebas demuestran la presencia de defectos, no su ausencia | Los 24 tests en verde confirman que la lógica de negocio cumple los contratos definidos; no garantizan ausencia total de bugs en producción con datos reales |
| **2** | El testing exhaustivo es imposible | `failedEvents` puede ser cualquier entero ≥ 0. Solo se cubren los 4 boundary values críticos (9, 10, 19, 20) que definen las transiciones de estado |
| **3** | Testing temprano — Shift Left | Los tests se escribieron **antes** de la implementación siguiendo TDD: RED (test falla) → GREEN (implementación mínima) → REFACTOR |
| **4** | Agrupación de defectos (Clustering)  | `OutboxHealthMetrics` concentra todas las reglas de negocio críticas → recibe 12 de los 24 tests unitarios (50% de la suite) |
| **5** | Paradoja del pesticida | Se varían los tipos de prueba: flujo feliz, valor nulo, excepción, boundary exacto, misconfiguration, prioridad de estados para evitar que los mismos tests dejen de detectar nuevos bugs |
| **6** | El testing depende del contexto | Es un microservicio de observabilidad backend-only, consumido por Kubernetes probes y herramientas de monitoring. No hay UI → sin tests E2E de formularios; la cobertura E2E es 100% API |
| **7** | Falacia de ausencia de errores | 24 tests unitarios en verde no significa que el worker funcione correctamente con BD real, RabbitMQ real y carga de producción → por eso existe la Black-Box Suite (TS-03) con infraestructura real |

---

## 6. Técnicas de diseño de casos de prueba

### Caja Blanca — aplicada en TS-01

Se conoce la implementación de `OutboxHealthMetrics`. Los tests cubren cada rama condicional (`if`) del método `compute()`:

```
if (failedEvents >= downThreshold)        → TC-U05  (boundary =20)
if (elapsedSeconds > schedulerTimeout)    → TC-U09  (boundary >30s)
if (failedEvents >= warnThreshold)        → TC-U04  (boundary =10)
catch (Exception e)                       → TC-U03  (excepción BD)
lastProcessedAt.isEmpty()                 → TC-U11, TC-U02  (null)
```

**Boundary Value Analysis** — los valores exactos de frontera son los más propensos a bugs:

| Condición | Valor bajo (UP) | Valor frontera (WARN/DOWN) | Valor encima (DOWN) |
|-----------|----------------|--------------------------|---------------------|
| warnThreshold=10 | `failedEvents=9` → UP | `failedEvents=10` → WARN | — |
| downThreshold=20 | `failedEvents=19` → WARN | `failedEvents=20` → DOWN | — |
| schedulerTimeout=30s | `elapsed=29s` → UP | — | `elapsed=31s` → DOWN |

### Caja Negra — aplicada en TS-03

No se accede al código. Solo se usa el contrato de `health-endpoint.md`:
- Entrada: método HTTP, URL, estado de la BD
- Salida esperada: código HTTP, estructura JSON, valores de campos

---

## 7. Estrategia Multinivel y Pipeline CI

```
                    ┌─────────────────────────────────────┐
   Más lento        │  TS-03 Black-Box (AUTO_API_SCREENPLAY)│  ~30s — sistema real
   Más costoso      │  Serenity BDD + REST Assured          │
                    ├─────────────────────────────────────┤
                    │  TS-02 Integration                   │  ~60s — Postgres + RabbitMQ
                    │  Spring Boot Test + Docker services   │
                    ├─────────────────────────────────────┤
   Más rápido       │  TS-01 Unit (24 tests)               │  ~15s — sin infraestructura
   Más barato       │  JUnit 5 + Mockito                   │
                    └─────────────────────────────────────┘
```

**Mapeo al pipeline CI** (`.github/workflows/ci.yml`):

| Job CI | Suite | Infraestructura | Bloquea merge |
|--------|-------|-----------------|---------------|
| `unit-tests` | TS-01 | Ninguna | ✅ Sí |
| `integration-tests` | TS-02 | Postgres 16 + RabbitMQ 3 | ✅ Sí |
| `docker-build-scan` | — | Docker + Trivy scan | ✅ Sí |

---

## 8. Cobertura de Requisitos Funcionales

| Requisito | Tests que lo cubren | Estado |
|-----------|--------------------|---------| 
| FR-001 Endpoint público sin auth | TC-B01 | ✅ |
| FR-002 Respuesta < 500ms | TC-B01 (medición de tiempo) | ✅ |
| FR-003 Status global UP/WARN/DOWN | TC-U13–15, TC-B01–04 | ✅ |
| FR-004 Componentes db, rabbit, outbox | TC-B01 | ✅ |
| FR-005 Métricas pendingEvents, failedEvents, lastProcessedAt | TC-U17, TC-B01 | ✅ |
| FR-006 WARN cuando failedEvents >= warnThreshold (10) | TC-U04, TC-B02 | ✅ |
| FR-007 DOWN cuando failedEvents >= downThreshold (20) | TC-U05, TC-B03 | ✅ |
| FR-008 DOWN cuando scheduler stalled > 30s | TC-U09, TC-B04 | ✅ |
| FR-009 Thresholds configurables vía properties | TC-U08 (misconfiguration) | ✅ |
| FR-010 lastProcessedAt mantenido en memoria por scheduler | TC-U18–20 | ✅ |
| FR-011 null lastProcessedAt indica no procesado desde startup | TC-U02, TC-U11, TC-B05 | ✅ |

**Cobertura total de FR**: 11/11 (100%)

---

## 9. Reportes

| Tipo | Herramienta | Ubicación | Comando |
|------|-------------|-----------|---------|
| Reporte de tests | JUnit XML | `build/reports/tests/test/index.html` | `./gradlew test` |
| Cobertura HTML | JaCoCo | `build/reports/jacoco/test/html/index.html` | `./gradlew test jacocoTestReport` |
| Cobertura XML | JaCoCo | `build/reports/jacoco/test/jacocoTestReport.xml` | `./gradlew test jacocoTestReport` |
| Reporte E2E | Serenity BDD | `target/site/serenity/index.html` | en `AUTO_API_SCREENPLAY` |

**Thresholds mínimos configurados en `build.gradle`**:
- Líneas: 80%
- Branches: 75%
