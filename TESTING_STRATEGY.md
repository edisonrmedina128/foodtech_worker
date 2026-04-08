# Testing Strategy — foodtech_worker

**Feature**: Worker Health Check (`001-worker-health-check`)
**Stack**: Java 25 · Spring Boot 3.5.11 · JUnit 5 · Mockito · JaCoCo
**Suite**: 24 unit tests — zero `@SpringBootTest`

---

## 1. QA vs. Testing

| Concepto | Descripción | Aplicado en este proyecto |
|----------|-------------|--------------------------|
| **QA (Quality Assurance)** | Estrategia global para prevenir defectos: arquitectura, revisión de código, TDD, CI/CD | Arquitectura Hexagonal que aísla la lógica de negocio · TDD con tests escritos antes de la implementación · Pipeline CI que bloquea merges con fallos |
| **Testing** | Ejecución de scripts que detectan defectos en un momento dado | `./gradlew test` · 24 tests en 3 clases · Reporte JaCoCo generado automáticamente |

**La diferencia clave**: QA es la disciplina; Testing es una de sus herramientas.

---

## 2. Verificar vs. Validar

### Verificar — "¿El código funciona técnicamente?"
> Confirmar que los mecanismos internos (puertos, mocks, delegación) se comportan como se diseñaron.

**Ejemplo en este proyecto** — `WorkerHealthIndicatorTest`:
```java
@Test
void health_includesAllMetricDetails_always() {
    when(outboxHealthMetrics.compute()).thenReturn(
            new OutboxHealthSnapshot(3, 1, LocalDateTime.now(), "UP", null));

    Health health = indicator.health();

    assertThat(health.getDetails())
            .containsKeys("pendingEvents", "failedEvents", "lastProcessedAt");
}
```
**Por qué es Verificar**: confirma que el adapter (`WorkerHealthIndicator`) traduce correctamente el snapshot al formato de Spring Actuator. No valida ninguna regla de negocio; verifica que la interfaz del puerto se llama y su resultado se mapea bien.

---

### Validar — "¿El negocio está protegido?"
> Confirmar que las reglas de dominio críticas se respetan, independientemente de la implementación.

**Ejemplo en este proyecto** — `OutboxHealthMetricsTest`:
```java
@Test
void compute_returnsDown_schedulerStallWins_overLowFailedEvents() {
    when(outboxRepositoryPort.countByStatus("FAILED")).thenReturn(2L); // bajo el umbral WARN
    when(schedulerStatePort.getLastProcessedAt())
            .thenReturn(Optional.of(LocalDateTime.now().minusSeconds(35)));

    OutboxHealthSnapshot snapshot = metrics.compute();

    assertThat(snapshot.status()).isEqualTo("DOWN");       // regla de negocio: DOWN gana
    assertThat(snapshot.reason()).contains("Scheduler stalled");
}
```
**Por qué es Validar**: no prueba una línea de código específica — prueba que la regla de negocio "un scheduler caído es DOWN independientemente de los failed events" se cumple. Si se borrara `OutboxHealthMetrics` y se reescribiera, este test seguiría siendo el contrato a cumplir.

---

## 3. Clasificación de los 24 Tests

### SchedulerTrackerTest (3 tests) — Verificar

| Test | Tipo | Justificación |
|------|------|---------------|
| `getLastProcessedAt_returnsEmpty_beforeFirstCall` | ✅ Verificar | Verifica estado inicial del componente |
| `recordExecution_setsLastProcessedAt` | ✅ Verificar | Verifica que el método registra timestamp |
| `recordExecution_calledTwice_returnsLatestTimestamp` | ✅ Verificar | Verifica comportamiento de actualización del AtomicReference |

### WorkerHealthIndicatorTest (5 tests) — Verificar + Validar mixto

| Test | Tipo | Justificación |
|------|------|---------------|
| `health_returnsUp_whenSnapshotIsUp` | ✅ Verificar | Verifica traducción de status al formato Actuator |
| `health_returnsWarn_whenSnapshotIsWarn` | ✅ Verificar | Verifica traducción de WARN a `Health.status("WARN")` |
| `health_returnsDown_whenSnapshotIsDown` | ✅ Verificar | Verifica traducción de DOWN al formato Actuator |
| `health_includesReason_whenReasonIsNotNull` | ✅ Verificar | Verifica que el detail "reason" se incluye condicionalmente |
| `health_includesAllMetricDetails_always` | ✅ Verificar | Verifica que los 3 keys del contrato siempre están presentes |

### OutboxHealthMetricsTest (12 tests) — Principalmente Validar

| Test | Tipo | Regla de negocio protegida |
|------|------|---------------------------|
| `compute_returnsUpWithMetrics_whenAllHealthy` | ✅ Verificar | Flujo feliz — baseline de métricas |
| `compute_nullLastProcessedAt_onFreshStartup` | 🛡️ Validar | Fresh start no es DOWN — es UP informativo |
| `compute_returnsDown_whenRepositoryThrows` | 🛡️ Validar | Fallo de BD → DOWN (no excepción sin manejar) |
| `compute_returnsWarn_whenFailedEventsEqualsWarnThreshold` | 🛡️ Validar | Boundary `>=` inclusive en warnThreshold=10 |
| `compute_returnsDown_whenFailedEventsEqualsDownThreshold` | 🛡️ Validar | Boundary `>=` inclusive en downThreshold=20 |
| `compute_returnsWarn_whenFailedEventsOneBeforeDownThreshold` | 🛡️ Validar | failedEvents=19 → WARN, no DOWN |
| `compute_returnsUp_whenFailedEventsBelowWarnThreshold` | 🛡️ Validar | failedEvents=9 → UP |
| `compute_downWins_whenMisconfiguredThresholdsAreEqual` | 🛡️ Validar | Misconfiguración: DOWN siempre gana sobre WARN |
| `compute_returnsDown_whenSchedulerStalled` | 🛡️ Validar | Scheduler > 30s → DOWN con razón legible |
| `compute_returnsUp_whenSchedulerWithinTimeout` | 🛡️ Validar | Scheduler < 30s → no afecta el estado |
| `compute_returnsUp_whenLastProcessedAtIsNull` | 🛡️ Validar | `Optional.empty()` en startup → UP, no DOWN |
| `compute_returnsDown_schedulerStallWins_overLowFailedEvents` | 🛡️ Validar | **Prioridad de estados**: DOWN scheduler > WARN failedEvents |

**Resumen**: 8 tests Verificar + 11 tests Validar + 5 tests mixtos.
La mayor densidad de tests de Validación está en `OutboxHealthMetrics` — la clase con toda la lógica de negocio crítica.

---

## 4. Estrategia Multinivel (Pirámide de Pruebas)

```
        ▲
       /E2E\          AUTO_API_SCREENPLAY — pruebas de caja negra
      /──────\         contra el endpoint real con Docker Compose
     /Integra-\
    /  ción    \      OutboxIntegrationTest — Postgres + RabbitMQ reales
   /────────────\
  /   Unitarias  \   24 tests — Mockito, sin Spring, sin BD, sin red
 /────────────────\
```

| Nivel | Tests | Velocidad | Aislamiento | Herramienta |
|-------|-------|-----------|-------------|-------------|
| Unit | 24 | ~800ms | Total (mocks) | JUnit 5 + Mockito |
| Integration | OutboxIntegrationTest | ~10s | Parcial (BD + Rabbit reales) | Spring Boot Test |
| E2E / Caja Negra | AUTO_API_SCREENPLAY | ~30s | Ninguno (sistema real) | Serenity BDD + REST Assured |

**Regla de oro aplicada**: la lógica crítica de negocio (`OutboxHealthMetrics`) vive en el nivel Unit, donde corre en milisegundos y puede cubrir todos los boundary values sin levantar infraestructura.

---

## 5. Cobertura JaCoCo

Ejecutar con:
```bash
./gradlew test jacocoTestReport
# Reporte en: build/reports/jacoco/test/html/index.html
```

Thresholds configurados en `build.gradle`:
- Líneas: mínimo **80%**
- Branches: mínimo **75%**

Las clases excluidas del análisis de cobertura por diseño:
- `FoodtechWorkerApplication` — clase de bootstrap
- Entidades JPA (`OutboxEntity`) — solo getters/setters Lombok
- `OutboxHealthSnapshot` — Java record (sin lógica de branches)
