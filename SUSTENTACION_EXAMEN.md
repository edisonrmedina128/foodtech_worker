# 🎓 Sustentación Examen Final — IA Engineer Full Cycle
## Feature: `001-worker-health-check` · `foodtech_worker`
### Roles: Developer · QA Automation Engineer · Analista de Calidad

---

> **Tiempo estimado de exposición oral: ~65 minutos**
> Cada sección tiene su tiempo sugerido. El evaluador puede interrumpir en cualquier punto.

---

## 🗺️ Mapa de la Exposición

| # | Sección | Dimensión Rúbrica | Tiempo |
|---|---------|-------------------|--------|
| 0 | Apertura y contexto del sistema | — | 5 min |
| 1 | Arquitectura Hexagonal y trazabilidad al diseño | Dev 1, Dev 2 | 15 min |
| 2 | Patrones tácticos y anti-smells | Dev 3, Dev 4 | 10 min |
| 3 | Estrategia de pruebas y pirámide | Test 1, Test 2 | 15 min |
| 4 | CI/CD, Docker, aislamiento | Test 3, Test 4 | 8 min |
| 5 | Automatización E2E — POM y API Screenplay | QA 1, QA 2, QA 3 | 10 min |
| 6 | Simbiosis Humano-IA: las 5 correcciones clave | HITL 1-4 | 10 min |
| 7 | Cierre ejecutivo y demo | — | 5 min |

---

## 🎙️ SECCIÓN 0 — Apertura y Contexto del Sistema *(5 min)*

### Lo que voy a decir:

> "Estoy presentando la feature `001-worker-health-check` del sistema **FoodTech Worker Service**. Antes de entrar al código, necesito contextualizar el problema de negocio porque toda decisión técnica que tomé —incluyendo las correcciones a la IA— tiene una razón de negocio detrás."

---

### El sistema y el problema real

**FoodTech Worker** es un microservicio de procesamiento asíncrono de órdenes de cocina. Su responsabilidad es leer eventos de una tabla **Outbox** (Postgres), publicarlos a **RabbitMQ** y marcarlos como procesados. Lo ejecuta un `@Scheduled` cada 3 segundos.

**El problema que resuelve la feature:**
Un operador de cocina no tenía visibilidad del estado interno del worker. Si el scheduler dejaba de correr, o si había eventos fallidos acumulándose, nadie lo sabía hasta que el pedido de un cliente se perdía en producción.

**La feature entrega:**
- Un endpoint `GET /actuator/health` que expone tres estados: `UP`, `WARN`, `DOWN`
- Una página web `/worker-health.html` con dashboard visual en tiempo real
- Alertas automáticas basadas en umbrales configurables sin tocar código

**Los actores del sistema:**
- **Operador de cocina**: consume el dashboard `/worker-health.html`
- **Sistema de monitoreo** (plataforma, alertas): consume `GET /actuator/health`
- **Developer/DevOps**: configura umbrales en `application.properties`

---

### Arquitectura general del sistema

```
┌─────────────────────────────────────────────────────────┐
│                    FOODTECH WORKER                        │
│                                                           │
│  [HTTP /actuator/health]  [HTTP /worker-health.html]     │
│           │                        │                      │
│    ┌──────▼────────────────────────▼──────┐              │
│    │         INFRASTRUCTURE LAYER          │              │
│    │  WorkerHealthIndicator  (Actuator)    │              │
│    │  TestEventController    (REST)        │              │
│    │  OutboxScheduler        (Scheduler)   │              │
│    │  HealthConfig, RabbitMqConfig         │              │
│    └───────────────┬───────────────────────┘              │
│                    │ usa (puertos)                         │
│    ┌───────────────▼───────────────────────┐              │
│    │         APPLICATION LAYER             │              │
│    │  OutboxHealthMetrics   (plain Java)   │              │
│    │  ProcessOutboxUseCase                 │              │
│    │  Ports: OutboxRepositoryPort          │              │
│    │         SchedulerStatePort  ← (HITL!) │              │
│    │         EventPublisherPort            │              │
│    └───────────────┬───────────────────────┘              │
│                    │ implementa (adaptadores)              │
│    ┌───────────────▼───────────────────────┐              │
│    │          DOMAIN LAYER                 │              │
│    │  OutboxEvent, FoodEvent               │              │
│    └───────────────────────────────────────┘              │
└─────────────────────────────────────────────────────────┘
```

---

## 🏛️ SECCIÓN 1 — Arquitectura Hexagonal y Trazabilidad al Diseño *(15 min)*

### Dev 1: Trazabilidad Diseño → Código

> "El evaluador puede pedirme que muestre el diseño de Semana 6 y lo trace hasta el código. Aquí está la correspondencia exacta."

| Decisión de Diseño (Semana 6) | Implementación en Código |
|-------------------------------|--------------------------|
| Core Domain sin frameworks | `OutboxHealthMetrics.java` — clase Java pura, cero imports de Spring |
| Puerto de salida para estado del scheduler | `SchedulerStatePort.java` — interface en `application/ports/output/` |
| Adaptador actuator en infra | `WorkerHealthIndicator.java` extiende `AbstractHealthIndicator` |
| Configuración como @Bean en infra | `HealthConfig.java` — instancia `OutboxHealthMetrics` como `@Bean` |
| Umbrales configurables sin código | `HealthThresholdConfig.java` con `@ConfigurationProperties` |
| WARN status personalizado | `Health.status("WARN")` + `management.endpoint.health.status.order` |

**Punto clave de trazabilidad:**
El diseño de Semana 6 especificaba que "la lógica de cálculo de salud debe vivir en el dominio/aplicación sin dependencias de frameworks". Eso se refleja literalmente en el código:

```java
// application/service/OutboxHealthMetrics.java
// ↑ CERO imports de org.springframework.*
import com.foodtech.kitchen.worker.foodtech_worker.application.ports.output.OutboxRepositoryPort;
import com.foodtech.kitchen.worker.foodtech_worker.application.ports.output.SchedulerStatePort;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
```

---

### Dev 2: Cohesión y Desacoplamiento — Inversión de Dependencias

> "Voy a explicar por qué este código respeta la Inversión de Dependencias y qué habría pasado si no lo hacía."

**El problema concreto:**
`OutboxHealthMetrics` (capa application) necesita saber cuándo corrió el scheduler por última vez. El scheduler vive en `SchedulerTracker.java` (capa infrastructure). Si hago esto:

```java
// ❌ VIOLACIÓN — application importando infrastructure
import com.foodtech.kitchen.worker.foodtech_worker.infrastructure.adapters.input.scheduler.SchedulerTracker;

public class OutboxHealthMetrics {
    private final SchedulerTracker tracker; // ← fuga de abstracción
}
```

La capa de aplicación quedaría acoplada a Spring Scheduling. Si mañana cambio el mecanismo de scheduling (de `@Scheduled` a Quartz, por ejemplo), tendría que modificar la lógica de negocio. Eso viola tanto Hexagonal como DIP.

**La solución que implementé:**

```java
// application/ports/output/SchedulerStatePort.java
// Puerto de salida — abstracción pura, cero frameworks
public interface SchedulerStatePort {
    Optional<LocalDateTime> getLastProcessedAt();
}
```

```java
// application/service/OutboxHealthMetrics.java
public class OutboxHealthMetrics {
    private final SchedulerStatePort schedulerStatePort; // ← depende de la abstracción
    // ...
}
```

```java
// infrastructure/adapters/input/scheduler/SchedulerTracker.java
// El adaptador implementa el puerto — la flecha de dependencia apunta hacia adentro
@Component
public class SchedulerTracker implements SchedulerStatePort {
    private volatile LocalDateTime lastProcessedAt;
    // ...
}
```

**Resultado:** La flecha de dependencia apunta siempre hacia el interior del hexágono. `OutboxHealthMetrics` no sabe nada de Spring. `SchedulerTracker` sí sabe de Spring, pero eso está en infrastructure, donde es correcto.

---

### La regla de las 3 capas — y cómo la defiendo

Si el evaluador me pregunta "¿cómo sé que no hay fugas?", señalo estas 3 evidencias:

1. **`OutboxHealthMetrics.java`** — 0 imports de `org.springframework.*`
2. **`OutboxRepositoryPort.java`** — interfaz pura Java, sin `@Repository` ni JPA
3. **`SchedulerStatePort.java`** — interfaz de 1 sola línea, sin ninguna anotación

La única clase de aplicación que *se acerca* a infrastructure es `ProcessOutboxUseCase`, pero a través de sus puertos, nunca directamente.

---

## 🔧 SECCIÓN 2 — Patrones Tácticos y Anti-Smells *(10 min)*

### Dev 3: Patrón Factory para resolver complejidad real

> "No usé patrones por usar. El patrón Factory resuelve un problema concreto en este proyecto."

**¿Cuál era la complejidad real?**

`OutboxHealthMetrics` no puede ser un `@Service` (violaría Hexagonal), pero necesita recibir `OutboxRepositoryPort`, `SchedulerStatePort`, `warnThreshold`, `downThreshold` y `schedulerTimeoutSeconds`. ¿Cómo lo inyecta Spring sin que la clase tenga anotaciones?

**El patrón Factory resuelve esto:**

```java
// infrastructure/config/HealthConfig.java — Factory Method
@Configuration
public class HealthConfig {

    @Bean
    public OutboxHealthMetrics outboxHealthMetrics(
            OutboxRepositoryPort outboxRepositoryPort,  // inyección por puerto
            SchedulerStatePort schedulerStatePort,       // inyección por puerto
            HealthThresholdConfig config) {

        int warnThreshold = config.getWarnThreshold();
        int downThreshold = config.getDownThreshold();

        // Guardia de configuración incorrecta
        if (downThreshold <= warnThreshold) {
            log.warn("[HealthConfig] Misconfiguration: down-threshold ({}) must be > warn-threshold ({}). "
                    + "Adjusting down-threshold to {}", downThreshold, warnThreshold, warnThreshold + 1);
            downThreshold = warnThreshold + 1;
        }

        return new OutboxHealthMetrics(outboxRepositoryPort, schedulerStatePort,
                warnThreshold, downThreshold, config.getSchedulerTimeoutSeconds());
    }
}
```

**Por qué es más escalable:**
- Cambiar los umbrales → solo toco `application.properties`, no el código
- Cambiar la implementación del repositorio → Spring inyecta la nueva implementación automáticamente
- Agregar un nuevo puerto → solo agrego el parámetro al `@Bean`

---

### Dev 4: Refinamiento Sintáctico — Sin AI Smells

> "El código que ves aquí no es el primer borrador de la IA. Lo refactoricé específicamente para eliminar 4 tipos de AI Smells."

**AI Smell 1 eliminado — Variable genérica `result`:**
```java
// ❌ Como lo propuso la IA
HealthStatus result = computeStatus(events);
return result;

// ✅ Como quedó en el código final
OutboxHealthSnapshot snapshot = metrics.compute();
```

**AI Smell 2 eliminado — Método `calculateHealth()` de 60 líneas:**
La IA propuso un único método enorme. Lo rompí en responsabilidades claras:
- `compute()` → orquesta el flujo
- `OutboxHealthSnapshot` (record) → transporta el resultado inmutablemente
- `HealthConfig` → centraliza la construcción

**AI Smell 3 eliminado — `try-catch` vacío:**
```java
// ❌ Como lo propuso la IA
try {
    failedEvents = outboxRepository.countFailed();
} catch (Exception e) {
    // ignorado silenciosamente
}

// ✅ Como quedó
try {
    failedEvents = outboxRepositoryPort.countByStatus("FAILED");
} catch (Exception e) {
    return new OutboxHealthSnapshot(0, 0, null, "DOWN",
            "Could not query outbox metrics: " + e.getMessage());
}
```

**AI Smell 4 eliminado — Comentarios de sintaxis obvios:**
```java
// ❌ Como los puso la IA
// This method checks if the health is good
// Returns the health status
public OutboxHealthSnapshot compute() { ... }

// ✅ Comentario con valor arquitectónico real
/**
 * Plain Java class — no framework dependencies.
 * Instantiated as a Spring @Bean by HealthConfig in the infrastructure layer.
 */
public class OutboxHealthMetrics { ... }
```

---

## 🧪 SECCIÓN 3 — Estrategia de Pruebas y Pirámide *(15 min)*

### Test 1: La Pirámide de Testing — Estrategia deliberada

> "Tengo 24 pruebas unitarias y 3 de integración. Esa proporción es intencional."

**Mi pirámide:**
```
         /\
        /  \
       / E2E \   ← 0 (no hay UI compleja que probar con Selenium en backend)
      /--------\
     / Integrac. \  ← 3 pruebas (contratos de adaptadores)
    /--------------\
   /   Unitarias    \  ← 24 pruebas (lógica de negocio)
  /------------------\
```

**¿Por qué tantas unitarias?**
La lógica de `OutboxHealthMetrics.compute()` tiene múltiples ramas de decisión:
- DOWN por failedEvents crítico
- DOWN por stall de scheduler
- WARN por failedEvents moderado
- UP por startup fresco (null)
- UP normal
- DOWN por excepción en DB
- Prioridad DOWN > WARN cuando coexisten

Cada rama necesita ser probada **aisladamente**, sin levantar contexto de Spring, sin DB, sin red. Las unitarias lo hacen en milisegundos.

**¿Por qué pocas de integración?**
Las pruebas de integración verifican los **contratos de los adaptadores** (¿el SQL funciona? ¿RabbitMQ recibe el mensaje?), no la lógica de negocio. No tiene sentido repetir en integración lo que ya verifiqué unitariamente.

---

### Test 2: Verificar vs. Validar — Casos que protegen el negocio real

> "No escribí pruebas que solo verifican que el código compila. Cada prueba valida una regla de negocio."

**Prueba de valor límite — la más importante:**

```java
@Test
@DisplayName("returns WARN when failedEvents equals warnThreshold (boundary inclusive)")
void compute_returnsWarn_whenFailedEventsEqualsWarnThreshold() {
    when(outboxRepositoryPort.countByStatus("FAILED")).thenReturn(10L); // exactamente el umbral
    when(schedulerStatePort.getLastProcessedAt()).thenReturn(Optional.of(LocalDateTime.now()));

    OutboxHealthSnapshot snapshot = metrics.compute();

    assertThat(snapshot.status()).isEqualTo("WARN");
}
```

**¿Por qué es crítica?** La IA usó `>` (exclusivo) en lugar de `>=` (inclusivo). Con `>`, si failedEvents == 10 y el umbral es 10, el sistema reporta `UP` cuando debería reportar `WARN`. En producción eso significa que una alerta real se silencia. Esta prueba atrapa ese bug.

---

**Prueba de prioridad de estados — regla de negocio que la IA ignoró:**

```java
@Test
@DisplayName("DOWN from scheduler stall wins over failedEvents below warnThreshold")
void compute_returnsDown_schedulerStallWins_overLowFailedEvents() {
    when(outboxRepositoryPort.countByStatus("FAILED")).thenReturn(2L); // bajo el umbral WARN
    when(schedulerStatePort.getLastProcessedAt())
            .thenReturn(Optional.of(LocalDateTime.now().minusSeconds(35))); // stall

    OutboxHealthSnapshot snapshot = metrics.compute();

    assertThat(snapshot.status()).isEqualTo("DOWN"); // DOWN gana
    assertThat(snapshot.reason()).contains("Scheduler stalled");
}
```

**¿Por qué es crítica?** La IA nunca consideró que ambas condiciones pueden coexistir simultáneamente. Sin esta prueba, el sistema podría reportar `UP` cuando hay stall + pocos eventos fallidos.

---

**Prueba de null = UP (regla de startup):**

```java
@Test
@DisplayName("returns UP with informational reason when lastProcessedAt is null (fresh startup)")
void compute_returnsUp_whenLastProcessedAtIsNull() {
    when(schedulerStatePort.getLastProcessedAt()).thenReturn(Optional.empty());

    OutboxHealthSnapshot snapshot = metrics.compute();

    assertThat(snapshot.status()).isEqualTo("UP");
    assertThat(snapshot.reason()).isEqualTo("No events processed since last startup");
}
```

**¿Por qué es crítica?** La IA generó `null → DOWN`. Pero si el worker recién arrancó y no ha procesado ningún evento todavía, no es un error: es el estado normal de inicio. `null → DOWN` generaría una falsa alarma en cada deploy. La prueba valida la regla de negocio correcta.

---

### Clasificación completa: Verificar vs. Validar

| Prueba | Tipo | Regla de Negocio Validada |
|--------|------|---------------------------|
| `compute_returnsUpWithMetrics_whenAllHealthy` | Validar | Sistema sano → reporte correcto |
| `compute_nullLastProcessedAt_onFreshStartup` | Validar | null ≠ DOWN (startup limpio) |
| `compute_returnsDown_whenRepositoryThrows` | Validar | Fallo de DB → DOWN con mensaje |
| `compute_returnsWarn_whenFailedEventsEqualsWarnThreshold` | Validar | Boundary inclusive `>=` |
| `compute_returnsDown_whenFailedEventsEqualsDownThreshold` | Validar | Boundary inclusive `>=` |
| `compute_returnsWarn_whenFailedEventsOneBeforeDownThreshold` | Validar | Exactamente debajo del límite |
| `compute_returnsUp_whenFailedEventsBelowWarnThreshold` | Validar | Un evento debajo del umbral |
| `compute_downWins_whenMisconfiguredThresholdsAreEqual` | Validar | DOWN > WARN en colisión |
| `compute_returnsDown_whenSchedulerStalled` | Validar | Stall detection funciona |
| `compute_returnsUp_whenSchedulerWithinTimeout` | Validar | Dentro del timeout → UP |
| `compute_returnsDown_schedulerStallWins_overLowFailedEvents` | Validar | Prioridad DOWN > WARN |

---

## 🐳 SECCIÓN 4 — CI/CD, Docker y Aislamiento *(8 min)*

### Test 3: Pipeline de Despliegue Continuo como Quality Gate

> "Mi pipeline no es decorativo. Es un **quality gate estricto** con 3 trabajos que bloquean el merge si cualquiera falla."

**Arquitectura del pipeline:**
```
push/PR
   │
   ▼
┌─────────────────────────────────────────────────────────┐
│  Job 1: unit-tests                                       │
│  ● ./gradlew test jacocoTestReport                       │
│  ● JaCoCo: lines ≥ 80%, branches ≥ 75%                 │
│  ● Sube artifacts: jacoco-report, jacoco-xml             │
│  ● Duración: ~45s (sin infraestructura)                  │
└──────────────────┬──────────────────────────────────────┘
                   │ needs: unit-tests
         ┌─────────┴─────────┐
         ▼                   ▼
┌────────────────┐  ┌──────────────────────────────────────┐
│  Job 2:        │  │  Job 3:                               │
│  integration-  │  │  docker-build-scan                    │
│  tests         │  │  ● docker build -t foodtech-worker:ci │
│  ● Postgres 16 │  │  ● Trivy: HIGH,CRITICAL vulnerabilities│
│  ● RabbitMQ 3  │  │  ● Falla si hay vulns críticas         │
│  ● -Pinclude   │  └──────────────────────────────────────┘
│    Integration │
└────────────────┘
```

**Por qué los Jobs 2 y 3 dependen del Job 1:**
Si las unitarias fallan, no tiene sentido levantar Postgres y RabbitMQ para ejecutar integración. El `needs: unit-tests` ahorra ~2 minutos de infra cloud y da feedback más rápido.

---

### Test 3: Aislamiento total en unitarias — Mocks vs. Integración

**Las unitarias NO levantan contexto de Spring:**

```java
@ExtendWith(MockitoExtension.class) // ← solo Mockito, sin @SpringBootTest
class OutboxHealthMetricsTest {

    @Mock private OutboxRepositoryPort outboxRepositoryPort; // mock del puerto
    @Mock private SchedulerStatePort schedulerStatePort;     // mock del puerto

    @BeforeEach
    void setUp() {
        metrics = new OutboxHealthMetrics(outboxRepositoryPort, schedulerStatePort, 10, 20, 30);
    }
```

**Resultado:** Cada prueba unitaria corre en **< 5ms**. El suite completo de 24 pruebas corre en **< 800ms**. Si usara `@SpringBootTest` correría en +30 segundos y dependería de Postgres y RabbitMQ.

---

### Test 4: Pruebas de Integración — Contratos de Adaptadores

**`OutboxIntegrationTest`** verifica el contrato del adaptador de repositorio:
- ¿El SQL de `countByStatus("NEW")` cuenta correctamente?
- ¿El `save()` persiste correctamente?
- ¿Los datos se limpian entre escenarios (teardown)?

Usa **Postgres 16 real** (levantado como GitHub Actions service), no H2 embebido. Esto es importante porque H2 tiene dialectos distintos a Postgres y puede enmascarar bugs de SQL.

**`RabbitMqIntegrationTest`** verifica el contrato del publisher:
- ¿El mensaje llega a la queue correcta?
- ¿El routing key es correcto?

---

## 🤖 SECCIÓN 5 — Automatización E2E y API Screenplay *(10 min)*

### QA 1: Los 3 repositorios de automatización

| Repo | Patrón | Estado | Qué prueba |
|------|--------|--------|------------|
| `foodtech_worker` | Core (Spring Boot) | ✅ | Lógica + API |
| `worker-front-pom-factory` | Page Object Model | ✅ | UI `/worker-health.html` |
| `worker-api-screenplay` | Screenplay REST | ✅ | `GET /actuator/health` |

---

### QA 1: Arquitectura POM — @FindBy estricto, sin lógica

> "El Page Object no tiene lógica de negocio. Tiene @FindBy declarativos y métodos que exponen comportamiento, no clics."

```java
// worker-front-pom-factory: WorkerHealthPage.java
public class WorkerHealthPage extends BasePage {

    @FindBy(css = "[data-testid='health-status-badge']")
    private WebElement statusBadge;    // ← @FindBy puro, sin lógica

    @FindBy(css = "[data-testid='worker-health-title']")
    private WebElement pageTitle;

    // Método que expone COMPORTAMIENTO, no mecánica de UI
    public String getDisplayedStatus() {
        return waits.waitForVisible(
            By.cssSelector("[data-testid='health-status-badge']")).getText().trim();
    }

    // ✅ Ningún método tiene assertThat() — las aserciones viven en StepDefinitions
}
```

**Contrato de data-testid** — el dashboard HTML tiene los mismos selectores:
```html
<!-- worker-health.html en foodtech_worker -->
<span data-testid="health-status-badge" class="badge badge-loading">Loading...</span>
<span data-testid="pending-events-value">—</span>
<span data-testid="failed-events-value">—</span>
<span data-testid="last-processed-at-value">—</span>
```

Los 3 artefactos (Spring Boot, HTML, POM) están conectados por el mismo contrato de `data-testid`.

---

### QA 1: Arquitectura Screenplay — SRP a nivel de Tasks

> "Cada clase tiene exactamente una razón para cambiar."

```
Ability  → CallAnApi         — sabe conectarse a la API (baseUrl)
Interaction → GetWorkerHealthStatus — sabe hacer GET /actuator/health
Task     → CheckWorkerHealth — orquesta: GET → deserializar → recordar
Question → TheWorkerHealthStatus  — sabe extraer globalStatus, outboxStatus, httpCode
Question → TheWorkerOutboxDetails — sabe si los detalles tienen las 3 claves
```

**Si el endpoint cambia de `/actuator/health` a `/api/health`:** solo modifico `GetWorkerHealthStatus`. El resto no cambia.

**Si el modelo de respuesta cambia:** solo modifico `WorkerHealthResponse`. El resto no cambia.

Eso es SRP aplicado al patrón Screenplay.

---

### QA 2: BDD Gherkin — Lenguaje declarativo de negocio

**API Screenplay feature (declarativo, centrado en negocio):**
```gherkin
@worker-health-up
Scenario: Worker reports UP when all components are healthy
  When the monitor queries the worker health endpoint
  Then the HTTP response code is 200
  And the global health status is "UP"
  And the workerOutbox component status is "UP"
  And the workerOutbox details include pendingEvents, failedEvents and lastProcessedAt
```

**POM feature (declarativo, centrado en operador):**
```gherkin
@worker-health-ui-warn
Scenario: Monitoring page displays WARN status when failed events exceed threshold
  Given the operator navigates to the worker health monitor page
  And the worker has exceeded the failed events threshold
  Then the displayed health status is "WARN"
  And the metrics section shows pending events
  And the metrics section shows failed events
```

No dice "hago clic en el botón X" ni "ingreso valor en el campo Y". Describe **qué** pasa, no **cómo** Selenium lo hace.

---

### QA 3: API Screenplay — Flujo robusto con aserciones profundas

**No solo validamos status 200. Validamos el contrato JSON completo:**

```java
// TheWorkerOutboxDetails.java
public static Question<Boolean> containsPendingEvents() {
    return actor -> {
        WorkerHealthResponse h = actor.recall(ScenarioMemoryKeys.WORKER_HEALTH_RESPONSE);
        WorkerHealthResponse.ComponentHealth outbox = h.getComponents().get("workerOutbox");
        return outbox != null && outbox.getDetails() != null
               && outbox.getDetails().containsKey("pendingEvents"); // ← valida esquema JSON
    };
}
```

**Cobertura de status codes:**
| Scenario | Status Code | Body status |
|----------|-------------|-------------|
| Healthy | 200 | `"UP"` |
| Warn threshold | 200 | `"WARN"` |
| Critical threshold | 503 | `"DOWN"` |
| Scheduler stall | 503 | `"DOWN"` |

Spring Actuator retorna 503 automáticamente cuando el estado es `DOWN`. Eso también está probado.

---

## 🧠 SECCIÓN 6 — Simbiosis Humano-IA: Las 5 Correcciones Clave *(10 min)*

> "Esta es la parte más importante para mí. Quiero demostrar que yo dirigí a la IA, no al revés."

---

### HITL 1: Corrección Arquitectónica — `@Service` rechazado

**Lo que propuso la IA:**
```java
// ❌ Propuesta de la IA
@Service  // ← anotación de Spring en la capa de aplicación
public class OutboxHealthMetrics {
    @Autowired
    private OutboxRepository outboxRepository; // ← importa JPA directamente
```

**Por qué lo rechacé:**
La capa de aplicación no puede tener `@Service`. Esa anotación le indica a Spring que escanee y gestione la clase, acoplando el dominio al framework. Si mañana migramos de Spring a Quarkus o Micronaut, tendríamos que modificar la lógica de negocio. Eso viola directamente el Principio I de la Arquitectura Hexagonal.

**Lo que decidí:**
```java
// ✅ Mi decisión
// Sin @Service, sin @Autowired, sin imports de Spring
public class OutboxHealthMetrics {
    // Constructor injection via HealthConfig @Bean
```

---

### HITL 2: Creación del Puerto `SchedulerStatePort` — no propuesto por la IA

**La IA propuso acoplamiento directo:**
```java
// ❌ Propuesta de la IA
public class OutboxHealthMetrics {
    private final SchedulerTracker schedulerTracker; // ← class de infrastructure
```

**El problema:** `SchedulerTracker` vive en `infrastructure/adapters/input/scheduler/`. Si `OutboxHealthMetrics` (application layer) lo importa, la flecha de dependencia apunta en la dirección equivocada.

**Mi solución — crear un puerto que la IA no consideró:**

```java
// ✅ Puerto creado por mí, no por la IA
// application/ports/output/SchedulerStatePort.java
public interface SchedulerStatePort {
    Optional<LocalDateTime> getLastProcessedAt();
}
```

Luego hice que `SchedulerTracker` implemente ese puerto. La flecha de dependencia ahora apunta hacia adentro del hexágono. La IA no propuso esto por iniciativa propia.

---

### HITL 3: Corrección de valores límite — `>=` vs `>`

**Lo que generó la IA:**
```java
// ❌ Bug silencioso de la IA
if (failedEvents > warnThreshold) {  // exclusivo: 10 > 10 es false
    return WARN;
}
```

**El bug:** Con umbral de 10, si hay exactamente 10 eventos fallidos, el sistema reporta `UP` cuando debería reportar `WARN`. En producción, una alerta real se silencia.

**Mi corrección:**
```java
// ✅ Boundary inclusive
if (failedEvents >= warnThreshold) {  // inclusivo: 10 >= 10 es true
    return WARN;
}
```

**La prueba que captura este bug:**
```java
@Test
void compute_returnsWarn_whenFailedEventsEqualsWarnThreshold() {
    when(outboxRepositoryPort.countByStatus("FAILED")).thenReturn(10L); // exactamente en el límite
    assertThat(metrics.compute().status()).isEqualTo("WARN"); // captura el bug si fuera >
}
```

---

### HITL 4: Corrección de la semántica de `null` — `null ≠ DOWN`

**Lo que propuso la IA:**
```java
// ❌ Falsa alarma en cada deploy
if (lastProcessedAt == null) {
    return DOWN; // el worker acabó de arrancar → DOWN
}
```

**El problema de negocio:** Cuando el servicio se despliega, los primeros segundos `lastProcessedAt` es `null` porque el scheduler no ha corrido aún. Si `null → DOWN`, entonces **cada deploy genera una alerta de emergencia**. Los operadores empezarían a ignorar las alertas (efecto "lobo que grita").

**Mi corrección con razonamiento de negocio:**
```java
// ✅ null = startup limpio = UP informativo
if (lastProcessedAt.isPresent()) {
    long elapsed = Duration.between(lastProcessedAt.get(), LocalDateTime.now()).getSeconds();
    if (elapsed > schedulerTimeoutSeconds) {
        return DOWN; // solo es DOWN si se conoce el tiempo y está vencido
    }
}
// Si lastProcessedAt es empty → UP (startup reciente)
```

---

### HITL 5: Corrección de la regla de prioridad — DOWN > WARN

**Lo que omitió la IA:**
La IA no consideró el escenario donde **ambas condiciones son verdaderas al mismo tiempo**: hay stall de scheduler Y hay failedEvents en el rango WARN.

```java
// ❌ Problema de la IA — no hay regla de prioridad
if (failedEvents >= warnThreshold) return WARN;
if (schedulerStalled) return DOWN;
// Si warnThreshold se cumple primero, nunca llega a detectar el stall
```

**Mi corrección — orden deliberado de evaluación:**
```java
// ✅ DOWN siempre se evalúa antes que WARN
if (failedEvents >= downThreshold) return DOWN;     // crítico primero
if (schedulerStalled) return DOWN;                  // stall también es crítico
if (failedEvents >= warnThreshold) return WARN;     // solo si no hay DOWN
return UP;
```

El orden del código **es la regla de negocio**. DOWN tiene prioridad porque es la condición más crítica.

---

### Tabla resumen HITL

| # | Lo que propuso la IA | Lo que decidí yo | Impacto en Producción |
|---|---------------------|------------------|-----------------------|
| 1 | `@Service` en application | Plain Java + `@Bean` en infra | Portabilidad del dominio |
| 2 | Acoplar a `SchedulerTracker` | Crear `SchedulerStatePort` | Abstracción correcta |
| 3 | `failedEvents > threshold` | `failedEvents >= threshold` | Alerta silenciada en el límite |
| 4 | `null → DOWN` | `null → UP` (startup) | Falsa alarma en cada deploy |
| 5 | Sin regla de prioridad | DOWN > WARN explícito | Estado incorrecto cuando coexisten |

---

## 🎬 SECCIÓN 7 — Cierre Ejecutivo y Demo *(5 min)*

### Lo que entregué — checklist completo

| Entregable | Estado | Ubicación |
|---|---|---|
| Feature implementada (Hexagonal + SOLID) | ✅ | `src/main/java/` |
| 24 pruebas unitarias | ✅ | `OutboxHealthMetricsTest`, etc. |
| 3 pruebas de integración | ✅ | `OutboxIntegrationTest`, `RabbitMqIntegrationTest` |
| Pipeline CI/CD con 3 jobs | ✅ | `.github/workflows/ci.yml` |
| Dashboard UI `/worker-health.html` | ✅ | `src/main/resources/static/` |
| POM Factory automatización UI | ✅ | `worker-front-pom-factory/` |
| API Screenplay automatización REST | ✅ | `worker-api-screenplay/` |
| `BUSINESS_CONTEXT.md` | ✅ | repo raíz |
| `USER_STORIES_REFINEMENT.md` | ✅ | repo raíz |
| `TEST_CASES_AI.md` | ✅ | repo raíz |
| `TESTING_STRATEGY.md` | ✅ | repo raíz |
| `TEST_PLAN.md` con CP-01..CP-10 | ✅ | repo raíz |
| Branch `develop` + tag `v1.0.0` | ✅ | git |

---

### Demo en vivo — secuencia recomendada

```bash
# 1. Mostrar estructura de paquetes (Hexagonal)
tree src/main/java --charset utf8

# 2. Mostrar OutboxHealthMetrics.java — la clase central, sin Spring
cat src/main/java/.../application/service/OutboxHealthMetrics.java

# 3. Mostrar SchedulerStatePort — el puerto que yo creé
cat src/main/java/.../application/ports/output/SchedulerStatePort.java

# 4. Correr las pruebas unitarias
./gradlew test --no-daemon

# 5. Mostrar el reporte JaCoCo
start build/reports/jacoco/test/html/index.html

# 6. Mostrar el pipeline CI en GitHub Actions
# (abrir GitHub → Actions → último run verde)

# 7. Mostrar la feature Gherkin del API Screenplay
cat worker-api-screenplay/src/test/resources/features/worker/worker-health-check.feature
```

---

### Preguntas anticipadas del evaluador — mis respuestas

**P: "¿Por qué `OutboxHealthMetrics` no tiene `@Service`?"**
> R: "Porque es una clase de la capa de aplicación que implementa lógica de negocio pura. Ponerle `@Service` crearía una dependencia de Spring en el core domain, violando el Principio I de la Arquitectura Hexagonal: el dominio no debe conocer el framework. La solución fue instanciarla como `@Bean` en `HealthConfig`, que vive en infrastructure."

**P: "¿Qué pasa si configuro `down-threshold = warn-threshold`?"**
> R: "`HealthConfig` detecta la misconfiguration en el método `@Bean` y ajusta automáticamente `downThreshold = warnThreshold + 1`, logueando un `WARN` para que el equipo lo corrija. El sistema sigue funcionando correctamente."

**P: "¿Por qué `lastProcessedAt` está en memoria y no en la base de datos?"**
> R: "Es un trade-off deliberado. El scheduler corre cada 3 segundos. Si persistiera `lastProcessedAt` en DB en cada ejecución, tendríamos 20 escrituras por minuto solo para un campo de monitoreo. En memoria es suficiente para el objetivo de la feature, y el endpoint de health explica que el valor se reinicia en cada startup. Si el requerimiento de persistencia entre reinicios fuera crítico, lo agregaría como una iteración futura."

**P: "¿Por qué `SchedulerTracker` implementa `SchedulerStatePort` siendo que está en infrastructure?"**
> R: "Exactamente porque está en infrastructure es correcto. Los adaptadores de infrastructure *implementan* los puertos de aplicación. La regla es: las flechas de dependencia siempre apuntan hacia adentro. `SchedulerTracker` depende de `SchedulerStatePort` (application), nunca al revés."

**P: "¿Qué diferencia hay entre tus pruebas unitarias y las de integración?"**
> R: "Las unitarias prueban la lógica de negocio de `OutboxHealthMetrics` con mocks de los puertos. No necesitan base de datos ni red. Corren en < 1ms cada una. Las de integración prueban que el adaptador `OutboxRepositoryAdapter` ejecuta el SQL correcto contra Postgres 16 real. Verifican el contrato del adaptador, no la lógica de negocio."

**P: "¿Cómo probás el estado WARN si Spring Actuator no tiene `Health.warn()`?"**
> R: "Uso `Health.status(new Status("WARN"))` con un string literal. Además, configuro el orden de estados en `application.properties`: `management.endpoint.health.status.order=DOWN,WARN,UP`. Esto le dice a Spring cómo ordenar la severidad cuando hay múltiples componentes con estados distintos."

**P: "¿Por qué el POM apunta a `localhost:8081` y no a una app frontend separada?"**
> R: "Porque la feature es un health check de infraestructura. Decidí agregar el dashboard directamente como recurso estático en Spring Boot en `/worker-health.html`. Spring sirve la página, la página llama `/actuator/health` con `fetch()` y renderiza el estado. Esto mantiene todo en un único deployable, lo cual es correcto para una feature de monitoreo interna."

---

### Frase de cierre

> "Esta feature no es solo un endpoint. Es la evidencia de un proceso de ingeniería donde cada decisión técnica tiene una justificación de negocio. `SchedulerStatePort` existe porque el equipo de operaciones necesita saber cuándo el scheduler se trabó, y hacerlo bien significa no violar la arquitectura. Las pruebas de valores límite existen porque un bug en `>` vs `>=` se traduce en alertas que nunca llegan. Y rechacé el `@Service` de la IA porque la portabilidad del dominio es un activo a largo plazo, no un detalle cosmético. Eso es lo que significa ser un IA Engineer Full Cycle."

---

*Generado con contexto completo del proyecto `foodtech_worker` · `001-worker-health-check`*
