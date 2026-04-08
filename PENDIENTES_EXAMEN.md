# 📋 Pendientes — Examen Final (Semanas 7 y 8)

**Feature implementada**: Worker Health Check (`001-worker-health-check`)
**Rama**: `001-worker-health-check`
**Estado Dev**: ✅ Completo — 24 tests unitarios en verde

---

## ✅ Lo que ya está listo (no tocar)

| Entregable | Estado |
|------------|--------|
| Código de la feature (Hexagonal + SOLID) | ✅ |
| `OutboxHealthMetrics` — plain Java, sin Spring | ✅ |
| `SchedulerStatePort` — puerto correcto en application layer | ✅ |
| `WorkerHealthIndicator` — Driver Adapter en infrastructure | ✅ |
| `SchedulerTracker` + `HealthThresholdConfig` + `HealthConfig` | ✅ |
| 24 pruebas unitarias con Mockito — cero `@SpringBootTest` | ✅ |
| Cobertura: happy path + edge cases (boundaries, null, stall, exception) | ✅ |
| Configuración externalizada en `application.properties` | ✅ |

---

## ❌ Pendientes para entregar el miércoles

### 1. AUTO_API_SCREENPLAY — Automatizar `GET /actuator/health`

**Prioridad: ALTA** — es el que sí aplica directamente a esta feature.

Escenarios a cubrir:

- [x] `GET /actuator/health` responde `200` y status `UP` con todos los componentes sanos
- [x] `GET /actuator/health` responde `200` y status `WARN` cuando `failedEvents >= 10`
- [x] `GET /actuator/health` responde `503` y status `DOWN` cuando `failedEvents >= 20`
- [x] `GET /actuator/health` responde `503` y status `DOWN` cuando el scheduler lleva > 30s sin correr
- [x] La respuesta siempre incluye `components.workerOutbox.details` con `pendingEvents`, `failedEvents`, `lastProcessedAt`

**Stack sugerido**: REST Assured + Screenplay pattern (Tasks / Questions / Abilities)

```
abilities/    → CallAnApi (baseUrl del worker)
tasks/        → QueryWorkerHealth.java
questions/    → WorkerStatus.java, OutboxDetails.java
features/     → WorkerHealthCheck.feature (Gherkin)
```

---

### 2. AUTO_FRONT_POM_FACTORY y AUTO_FRONT_SCREENPLAY

**Prioridad: MEDIA** — la feature es un API puro (no tiene UI propia).

Opciones:
- [x] Opción A: Agregar una página/pantalla de monitoreo en el frontend existente que consuma el endpoint y mostrar el estado — luego automatizar esa pantalla con POM/Screenplay Front.
- [x] Opción B: Documentar y justificar en la sustentación que esta feature es backend-only y que la cobertura E2E se hace 100% vía API Screenplay.

> 💡 **Recomendación**: Si el proyecto base ya tiene un frontend, agregar una pequeña vista de "Estado del Worker" y automatizarla. Si no, defender la opción B oralmente.

---

### 3. PR / Merge de la rama

- [x] Abrir Pull Request de `001-worker-health-check` → `main` (o la rama base del proyecto)
- [x] Incluir en la descripción del PR el Constitution Check (ya está en `plan.md`)
- [x] El PR debe estar listo antes de la sustentación

---

## 🎤 Prep para la Sustentación oral (6 min — Humano-IA)

Puntos que **debes poder explicar sin mirar el código**:

| Pregunta probable | Respuesta clave |
|-------------------|-----------------|
| ¿Por qué `OutboxHealthMetrics` no tiene `@Service`? | Principio I de la constitución: no Spring en application layer. Se instancia como `@Bean` en `HealthConfig` (infrastructure). |
| ¿Por qué creaste `SchedulerStatePort` en lugar de inyectar `SchedulerTracker` directo? | Hexagonal: la flecha de dependencia debe apuntar hacia adentro. Application no puede importar infrastructure. |
| ¿Qué pasa si `downThreshold <= warnThreshold`? | `HealthConfig` lo detecta, logea un warning y ajusta `downThreshold = warnThreshold + 1`. Hay un test que cubre esto. |
| ¿Por qué `lastProcessedAt` está en memoria y no en BD? | Trade-off consciente: evitar un write en BD cada 3 segundos. Se reinicia con el worker — documentado en `research.md`. |
| ¿Qué corrección arquitectónica hiciste tú sobre la IA? | La IA no propuso el `SchedulerStatePort`. Fui yo quien identificó que `OutboxHealthMetrics` no podía importar `SchedulerTracker` directamente sin violar Hexagonal. |
| ¿Qué cubre el test `compute_returnsDown_schedulerStallWins_overLowFailedEvents`? | Que un scheduler caído (DOWN) tiene mayor precedencia que failedEvents por debajo del umbral WARN — el DOWN siempre gana. |

---

## 🗂 Archivos entregables del repo core

```
src/main/java/.../application/ports/output/SchedulerStatePort.java   ← NUEVO
src/main/java/.../application/service/OutboxHealthMetrics.java        ← NUEVO
src/main/java/.../infrastructure/config/HealthThresholdConfig.java    ← NUEVO
src/main/java/.../infrastructure/config/HealthConfig.java             ← NUEVO
src/main/java/.../infrastructure/adapters/input/health/
    WorkerHealthIndicator.java                                         ← NUEVO
src/main/java/.../infrastructure/adapters/input/scheduler/
    SchedulerTracker.java                                              ← NUEVO
src/main/java/.../infrastructure/adapters/input/scheduler/
    OutboxScheduler.java                                               ← MODIFICADO
src/main/java/.../application/ports/output/OutboxRepositoryPort.java  ← MODIFICADO
src/main/java/.../infrastructure/persistence/repository/
    JpaOutboxRepository.java                                           ← MODIFICADO
src/main/java/.../infrastructure/persistence/adapter/
    OutboxRepositoryAdapter.java                                       ← MODIFICADO
src/main/resources/application.properties                             ← MODIFICADO
build.gradle                                                           ← MODIFICADO

src/test/java/.../application/service/OutboxHealthMetricsTest.java    ← NUEVO (12 tests)
src/test/java/.../infrastructure/adapters/input/health/
    WorkerHealthIndicatorTest.java                                     ← NUEVO (5 tests)
src/test/java/.../infrastructure/adapters/input/scheduler/
    SchedulerTrackerTest.java                                          ← NUEVO (3 tests)
```

---

---

## 📐 Semana 2 — TDD & Quality: "La Suite Inquebrantable"

### TESTING_STRATEGY.md

- [ ] Crear `TESTING_STRATEGY.md` en la raíz del repo diferenciando:
  - **Verificar** (pruebas técnicas): que los puertos se llamen correctamente, que `OutboxHealthMetrics` aísle infraestructura, que los mocks sean precisos
  - **Validar** (reglas de negocio): que `failedEvents >= downThreshold` → DOWN, que scheduler detenido → DOWN, que `lastProcessedAt = null` → UP (no DOWN)
  - **QA (estrategia general)** vs **Testing (ejecución de scripts)**: explicar qué cubre cada nivel (unit → integration → E2E)
  - Mostrar 1 ejemplo de test que Verifica (técnico) y 1 que Valida (negocio)

> 🎯 **Qué podemos hacer**: Generarlo directamente desde los tests existentes (24 tests), clasificando cada uno en "Verificar" o "Validar" con justificación técnica.

### JaCoCo — Reporte Automatizado de Cobertura

- [ ] Agregar plugin `jacoco` a `build.gradle`
- [ ] Configurar reporte HTML + XML con `jacocoTestReport`
- [ ] Configurar `jacocoTestCoverageVerification` con mínimo 80% de cobertura de líneas y branches
- [ ] Verificar que `./gradlew test jacocoTestReport` genera reporte en `build/reports/jacoco/`
- [ ] Captura de pantalla del reporte con 90%+ en verde

> 🎯 **Qué podemos hacer**: Agregar las 10 líneas de configuración en `build.gradle` y ejecutar el reporte. Cero código de producción nuevo.

---

## 🐳 Semana 3 — DevOps, CI/CD y Testing Multinivel

### Dockerfile — Infraestructura Inmutable

- [ ] Crear `Dockerfile` en la raíz con:
  - Multi-stage: etapa `build` con Gradle y etapa `runtime` con JRE ligero (`eclipse-temurin:21-jre-alpine`)
  - Ejecución sin privilegios root (`USER appuser`)
  - Copiar solo el JAR final, no el source code
- [ ] Crear `.dockerignore` (excluir `.git`, `build/`, `.gradle/`, etc.)
- [ ] Verificar que `docker build` genera imagen funcional
- [ ] Agregar `docker scout` o `trivy` scan para vulnerabilidades (evidencia de análisis)

> 🎯 **Qué podemos hacer**: Crear el Dockerfile multi-stage completo y el `.dockerignore`. El scan de vulnerabilidades se puede agregar como step en el pipeline.

### .github/workflows/ci.yml — Pipeline con Jobs Separados

- [ ] Crear `.github/workflows/ci.yml` con **jobs separados**:
  - **Job 1: `unit-tests`** — ejecuta `./gradlew test` (pruebas sin Spring context)
  - **Job 2: `integration-tests`** — levanta Postgres + RabbitMQ como services y ejecuta tests de integración
  - **Job 3: `build-and-scan`** — build Docker + análisis de vulnerabilidades
- [ ] El pipeline debe bloquearse en PR si cualquier job falla
- [ ] Publicar reporte JaCoCo como artifact del pipeline
- [ ] Badge de estado en README

> 🎯 **Qué podemos hacer**: Generar el YAML completo. Ya tenemos el esqueleto base en este archivo. Solo falta separar en jobs y agregar el Docker scan.

### TEST_PLAN.md — Informe Formal de Pruebas

- [ ] Crear `TEST_PLAN.md` como informe técnico profesional con:
  - **Test Suites**: Unit Suite, Integration Suite, Black-Box Suite (Caja Negra)
  - **Test Plan**: alcance, enfoque, herramientas (JUnit 5, Mockito, JaCoCo)
  - **Test Cases**: tabla con ID, descripción, tipo (Caja Blanca/Negra), resultado esperado
  - **Los 7 Principios del Testing** aplicados al proyecto:
    1. Las pruebas demuestran la presencia de defectos, no su ausencia
    2. Exhaustive testing es imposible → por eso usamos boundary values (umbral 10/20)
    3. Early testing → TDD con tests antes del código
    4. Clustering de defectos → `OutboxHealthMetrics` tiene la mayor densidad de lógica
    5. Paradoja del pesticida → variamos escenarios (null, boundary, stall)
    6. Testing depende del contexto → microservicio de observabilidad, no UI
    7. Falacia de ausencia de errores → tests en verde no garantizan valor de negocio
  - **Estrategia Multinivel**: pirámide Unit → Integration → E2E
  - **Distinción Caja Blanca vs Caja Negra**: ejemplos concretos del proyecto

> 🎯 **Qué podemos hacer**: Generar el documento completo partiendo de los 24 tests existentes y el contrato de la API. Es 100% documentación, sin código nuevo.

### GitFlow Formal — Release a main

- [ ] Crear rama `develop` desde `001-worker-health-check`
- [ ] Abrir PR formal: `develop` → `main` (Release)
  - Descripción con: qué feature se entrega, checklist de QA, Constitution Check
  - Tag de versión semántico: `v1.0.0`
- [ ] El PR debe disparar el pipeline y estar en verde antes del merge

> 🎯 **Qué podemos hacer**: Crear la rama `develop`, abrir el PR con descripción formal y agregar el tag `v1.0.0` post-merge.

---

## 🤖 Semana 4 — QA Moderno con IA (Repositorio separado)

> ⚠️ Estos entregables van en un **repositorio nuevo** (no en `foodtech_worker`).

### Tu tema asignado: "Estrategias y Pirámide de Automatización"

- [ ] Preparar presentación de 25-30 minutos con:
  - Por qué no todo se automatiza por UI (ROI en automatización)
  - Pirámide de pruebas: Unit → Integration → E2E (costos, velocidad, fragilidad)
  - Cuándo automatizar vs cuándo hacer testing manual
  - Antipatrones: pirámide invertida (ice cream cone), over-testing en UI
  - Demostración: mostrar en IDE cómo un test unitario corre en ms vs uno E2E en segundos
- [ ] PDF/diapositivas en carpeta del repositorio de la semana 4

> 🎯 **Qué podemos hacer**: Armar el contenido técnico con código Java comparativo (test unitario vs E2E) y generar el material de presentación.

### BUSINESS_CONTEXT.md

- [ ] Documentar el contexto de negocio de foodtech_worker usando la plantilla del curso:
  - Nombre del sistema, objetivo de negocio
  - Usuarios finales y sus necesidades
  - Restricciones y supuestos del dominio
  - KPIs de calidad del servicio

> 🎯 **Qué podemos hacer**: Generar desde `spec.md` + `research.md` existentes.

### USER_STORIES_REFINEMENT.md

- [ ] Tomar 3 HUs de la feature (`001-worker-health-check`)
- [ ] Para cada HU: versión original → versión refinada por IA → tabla de diferencias detectadas
- [ ] Aplicar principios INVEST al análisis

> 🎯 **Qué podemos hacer**: Las 4 HUs (US1–US4) están en `spec.md`. Refinamos 3 con SKAI/IA y documentamos las diferencias.

### TEST_CASES_AI.md

- [ ] Generar matriz de casos de prueba con IA para las 3 HUs seleccionadas
- [ ] Tabla de ajustes del probador: caso generado → ajuste → justificación técnica
- [ ] Mínimo 2 ajustes reales que demuestren análisis crítico (ej: IA olvidó validar null, IA no consideró boundary exacto)

> 🎯 **Qué podemos hacer**: Los casos de prueba ya existen como tests en el repo. Podemos invertir el proceso: mostrar los tests reales y demostrar que la IA los generó sin los edge cases de boundary que tú corregiste.

---

## 🎭 Semana 5 — Automatización POM y Screenplay (3 repositorios)

> ⚠️ Esta feature es backend-only (no tiene UI). Ver opciones:

### AUTO_FRONT_POM_FACTORY

- [ ] **Opción A (recomendada)**: Si el proyecto base tiene frontend → agregar pantalla "Estado del Worker" que consuma `/actuator/health` y automatizarla con POM + `@FindBy`
- [ ] **Opción B**: Usar otra aplicación web del curso y documentar en README por qué esta feature es API-only

> 🎯 **Qué podemos hacer**: Si hay frontend disponible, crear la vista y los Page Objects. Si no, seleccionar la app web más completa del curso.

### AUTO_FRONT_SCREENPLAY

- [ ] 2 escenarios NUEVOS (distintos a los de POM) con patrón Screenplay completo
  - Actor, Tasks, Actions, Questions bien separados
  - Al menos 1 flujo positivo + 1 negativo
  - Principio de responsabilidad única en cada Task

> 🎯 **Qué podemos hacer**: Diseñar escenarios de usuario diferentes (ej: navegación + verificación de estado visual vs. POM que testea el formulario).

### AUTO_API_SCREENPLAY ✅

- [x] `GET /actuator/health` → `200 UP`
- [x] `GET /actuator/health` → `200 WARN` (failedEvents ≥ 10)
- [x] `GET /actuator/health` → `503 DOWN` (failedEvents ≥ 20)
- [x] `GET /actuator/health` → `503 DOWN` (scheduler stalled)
- [x] Respuesta incluye `components.workerOutbox.details`

---

## 🧪 Pendiente: Plan de Pruebas — Caja Negra

> Documentar las pruebas desde afuera del sistema (sin ver el código),
> validando comportamiento observable vía el endpoint.

### Casos de prueba caja negra para `GET /actuator/health`

- [ ] **CP-01** — Estado UP: worker sano, 0 eventos fallidos → respuesta `200`, `status: UP`, `failedEvents: 0`
- [ ] **CP-02** — Estado WARN: simular 10+ eventos FAILED en BD → respuesta `200`, `status: WARN`
- [ ] **CP-03** — Estado DOWN por failedEvents: simular 20+ FAILED → respuesta `503`, `status: DOWN`
- [ ] **CP-04** — Estado DOWN por scheduler: detener el scheduler > 30s → respuesta `503`, `status: DOWN`
- [ ] **CP-05** — DB caída: bajar el contenedor de Postgres → respuesta `503`, componente `db: DOWN`
- [ ] **CP-06** — RabbitMQ caído: bajar el contenedor de Rabbit → respuesta `503`, componente `rabbit: DOWN`
- [ ] **CP-07** — Respuesta siempre incluye `pendingEvents`, `failedEvents`, `lastProcessedAt`
- [ ] **CP-08** — Tiempo de respuesta < 500ms en condiciones normales
- [ ] **CP-09** — El componente `worker` es independiente del estado de `db` y `rabbit`
- [ ] **CP-10** — `lastProcessedAt` es `null` justo después de un reinicio del worker

**Formato sugerido para documentar cada caso:**

```
ID:          CP-01
Tipo:        Caja Negra
Entrada:     GET http://localhost:8081/actuator/health (worker sano)
Esperado:    HTTP 200, body.status = "UP", body.components.worker.status = "UP"
Resultado:   [ PASS / FAIL ]
Evidencia:   Screenshot o curl output
```

---

## 🔁 Pendiente: CI/CD — Pipeline con Jobs Separados (Semana 3)

> Ver sección "Semana 3" arriba para el detalle completo.
> Resumen rápido de tareas concretas:

- [ ] Crear `.github/workflows/ci.yml` con 3 jobs separados (unit / integration / docker-scan)
- [ ] Levantar Postgres y RabbitMQ como services en el job de integración
- [ ] Publicar reporte JaCoCo como artifact
- [ ] Badge de estado en el README
- [ ] Configurar Branch Protection para bloquear merges si el pipeline falla

---

## 📚 Pendiente: Lo aprendido en el curso — sección de reflexión

> Para la sustentación o entrega: conectar lo implementado con los temas del curso.

- [ ] Redactar párrafo corto que conecte cada tema del curso con lo implementado:

| Tema del curso | Dónde se aplica en este proyecto |
|---|---|
| Arquitectura Hexagonal | `OutboxHealthMetrics` sin Spring, puertos, adapters |
| SOLID | SRP en cada clase; DIP en los puertos; OCP (umbrales configurables) |
| TDD / Testing | 24 tests unitarios escritos con Mockito, sin infraestructura |
| Patrones de diseño | Ports & Adapters, Adapter pattern, Strategy implícito en estados |
| Microservicios | Health Check API Pattern, Observabilidad, Outbox Pattern |
| Serenity BDD | Repos de automatización E2E (API Screenplay, POM, Front Screenplay) |
| CI/CD | Pipeline de GitHub Actions (pendiente de implementar) |
| Uso responsable de IA | CHANGELOG_SOURCES.md documenta correcciones a propuestas de la IA |

- [ ] Mencionar **al menos una corrección que hiciste a la IA** en la sustentación oral
  > *Ejemplo: "La IA propuso que `OutboxHealthMetrics` tuviera `@Service`. Yo lo rechacé porque viola Hexagonal."*
- [ ] Mencionar **al menos una fuente primaria** que consultaste
  > *Ejemplo: "Consulté el RFC 9457 de IETF para el formato del contrato de respuesta."*

---

*Actualizado: 2026-04-07 | Agregados: Plan de Pruebas, CI/CD, Reflexión del curso*
