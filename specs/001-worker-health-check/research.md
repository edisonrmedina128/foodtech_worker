# Research: Worker Health Check

**Feature**: `001-worker-health-check`
**Date**: 2026-04-06

## Decision 1: Spring Boot Actuator as the Health Framework

**Decision**: Use Spring Boot Actuator's `HealthIndicator` extension point
(`AbstractHealthIndicator`) rather than a custom REST endpoint.

**Rationale**: Spring Boot Actuator is already an industry-standard mechanism
for exposing operational health in the FoodTech ecosystem. It integrates
natively with Kubernetes liveness/readiness probes, Prometheus, and Grafana
without additional wiring. The built-in `db` and `rabbit` contributors provide
database and RabbitMQ status out of the box; only a custom `outbox` contributor
needs to be written.

**Alternatives considered**:
- Custom `@RestController` at `/health`: Rejected — reinvents what Actuator
  already provides; breaks standard tooling integration.
- Micrometer Gauge metrics at `/actuator/metrics`: Rejected — metrics are
  pull-based counters, not status indicators; they don't support UP/WARN/DOWN
  semantics natively.

---

## Decision 2: `OutboxHealthMetrics` as a Plain Java Application Service

**Decision**: `OutboxHealthMetrics` is a plain Java class (no Spring
annotations) in `application/service/`, instantiated as a `@Bean` from an
infrastructure `@Configuration` class (`HealthConfig`). Threshold values are
passed as constructor arguments.

**Rationale**: The constitution (Principle I) prohibits Spring annotations in
`application/` packages. Making `OutboxHealthMetrics` a plain class keeps it
framework-free and trivially unit-testable without a Spring context. The
infrastructure configuration layer is the correct place to wire Spring beans.

**Alternatives considered**:
- `@Service` annotation on `OutboxHealthMetrics`: Rejected — violates
  Hexagonal Architecture (Spring annotation in application layer).
- `@Value` fields in `OutboxHealthMetrics`: Rejected — same violation.
- Passing thresholds as method parameters on each call: Rejected — thresholds
  are stable configuration, not per-call input; constructor injection is cleaner.

---

## Decision 3: `SchedulerTracker` as an Infrastructure `@Component`

**Decision**: `SchedulerTracker` is a `@Component` in
`infrastructure/adapters/input/scheduler/` holding an
`AtomicReference<LocalDateTime>` for `lastProcessedAt`.

**Rationale**: State tracking of "when did the scheduler last run" is purely
an operational/infrastructure concern — it has no domain meaning. Placing it
in the scheduler adapter package collocates it with `OutboxScheduler`, which
is the only writer. `AtomicReference` ensures thread-safe reads by the health
indicator without synchronisation overhead.

**Alternatives considered**:
- Persisting `lastProcessedAt` to the database: Rejected — adds a DB write
  on every 3-second cycle; creates unnecessary overhead and coupling.
- Using a `volatile long` timestamp: Acceptable but `AtomicReference<LocalDateTime>`
  is more idiomatic and provides direct ISO-8601 serialisation.

---

## Decision 4: `countByStatus(String status)` Added to `OutboxRepositoryPort`

**Decision**: Extend `OutboxRepositoryPort` with a single new method:
`long countByStatus(String status)`. Call it twice from `OutboxHealthMetrics`
(once for `"NEW"`, once for `"FAILED"`) to derive `pendingEvents` and
`failedEvents`.

**Rationale**: This keeps the port focused (counts by status are a natural
repository query) and avoids exposing implementation details (JPA query DSL)
into the application layer. The `countByStatus` method is reusable for any
future consumer.

**Alternatives considered**:
- Adding a dedicated `OutboxMetricsPort`: Over-engineering for a single method;
  violates YAGNI.
- Returning a `Map<String,Long>` in one call: Reduces round-trips but complicates
  the port signature; premature optimisation for a low-frequency health check.
- Querying `findPendingEvents` and counting in memory: Loads full entities into
  memory just to count them; inefficient and wasteful.

---

## Decision 5: `WARN` Status Support via `Health.status()`

**Decision**: Spring Boot Actuator's `Health` builder does not have a built-in
`WARN` status. Use `Health.status(new Status("WARN")).withDetails(...).build()`
to create a custom WARN status. The global aggregation uses `OrderedHealthAggregator`
with `WARN` ordered between `UP` and `DOWN` (registered via custom
`StatusAggregator` bean if needed, or via
`management.endpoint.health.status.order=DOWN,WARN,UP` in properties).

**Rationale**: Actuator supports arbitrary custom statuses. The ordering must be
configured explicitly so the global status correctly degrades to the worst
component state.

**Alternatives considered**:
- Using `Health.unknown()` for the WARN state: Rejected — semantically
  incorrect; `UNKNOWN` means "cannot determine", not "threshold exceeded".
- Mapping WARN to DOWN: Rejected — loses the three-level alerting the spec
  requires (monitoring tools would alert at DOWN level, missing the warn window).

---

## Decision 6: `show-details=always` for the Health Endpoint

**Decision**: Set `management.endpoint.health.show-details=always` in
`application.properties`.

**Rationale**: The endpoint is consumed by internal Kubernetes probes and the
ops team's monitoring platform. Per the spec (FR-001), the endpoint is public
within the cluster network. Kubernetes liveness probes only check HTTP status
code (200 = UP, 503 = DOWN), but the ops team needs component details without
authentication. Network-level access control (not application-level auth)
governs exposure.

**Alternatives considered**:
- `show-details=when-authorized`: Rejected — requires auth configuration;
  over-complicates Kubernetes probe setup.
- `show-details=never`: Rejected — defeats US2 (component detail visibility).
