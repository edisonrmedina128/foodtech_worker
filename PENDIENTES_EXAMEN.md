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

- [ ] `GET /actuator/health` responde `200` y status `UP` con todos los componentes sanos
- [ ] `GET /actuator/health` responde `200` y status `WARN` cuando `failedEvents >= 10`
- [ ] `GET /actuator/health` responde `503` y status `DOWN` cuando `failedEvents >= 20`
- [ ] `GET /actuator/health` responde `503` y status `DOWN` cuando el scheduler lleva > 30s sin correr
- [ ] La respuesta siempre incluye `components.workerOutbox.details` con `pendingEvents`, `failedEvents`, `lastProcessedAt`

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
- [ ] Opción A: Agregar una página/pantalla de monitoreo en el frontend existente que consuma el endpoint y mostrar el estado — luego automatizar esa pantalla con POM/Screenplay Front.
- [ ] Opción B: Documentar y justificar en la sustentación que esta feature es backend-only y que la cobertura E2E se hace 100% vía API Screenplay.

> 💡 **Recomendación**: Si el proyecto base ya tiene un frontend, agregar una pequeña vista de "Estado del Worker" y automatizarla. Si no, defender la opción B oralmente.

---

### 3. PR / Merge de la rama

- [ ] Abrir Pull Request de `001-worker-health-check` → `main` (o la rama base del proyecto)
- [ ] Incluir en la descripción del PR el Constitution Check (ya está en `plan.md`)
- [ ] El PR debe estar listo antes de la sustentación

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

*Generado: 2026-04-06 | Rama: `001-worker-health-check`*
