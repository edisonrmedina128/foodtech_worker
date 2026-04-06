---

description: "Task list template for feature implementation"
---

# Tasks: Worker Health Check

**Input**: Design documents from `specs/001-worker-health-check/`
**Prerequisites**: plan.md ✅ · spec.md ✅ · research.md ✅ · data-model.md ✅ · contracts/ ✅

**Tests**: Included — unit tests paired with each production class per user request.
Each task is atomic and independently verifiable. Dependency order: infrastructure → ports → adapters → business logic → driver adapters → configuration → integration validation.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US4)
- Exact file paths included in every task description

## Path Conventions

All paths are relative to repository root.

```text
src/main/java/com/foodtech/kitchen/worker/foodtech_worker/
├── application/
│   ├── ports/output/         ← port interfaces
│   └── service/              ← plain Java application services (NO Spring imports)
├── domain/                   ← unchanged
└── infrastructure/
    ├── adapters/input/
    │   ├── health/           ← WorkerHealthIndicator (driver adapter)
    │   └── scheduler/        ← OutboxScheduler, SchedulerTracker
    ├── config/               ← @ConfigurationProperties, @Configuration beans
    └── persistence/
        ├── adapter/          ← OutboxRepositoryAdapter
        └── repository/       ← JpaOutboxRepository

src/test/java/com/foodtech/kitchen/worker/foodtech_worker/
├── application/service/
└── infrastructure/adapters/input/
    ├── health/
    └── scheduler/
```

---

## Phase 1: Setup (Infraestructura Base)

**Purpose**: Add Spring Boot Actuator dependency — required by all subsequent tasks.

- [x] T001 Add `implementation 'org.springframework.boot:spring-boot-starter-actuator'` to the `dependencies` block in `build.gradle` and verify `./gradlew dependencies` resolves it without conflicts

**Checkpoint**: `./gradlew build` passes with the new dependency.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared infrastructure required before any user story can be implemented.
All tasks in this phase can begin once T001 is complete; [P] tasks within this phase
can run in parallel with each other.

⚠️ **CRITICAL**: No user story implementation can begin until this phase is complete.

### Properties & Configuration

- [x] T002 Update `src/main/resources/application.properties` — add all health properties:
  `management.endpoints.web.exposure.include=health`,
  `management.endpoint.health.show-details=always`,
  `management.endpoint.health.status.order=DOWN,WARN,UP,UNKNOWN`,
  `foodtech.health.warn-threshold=10`,
  `foodtech.health.down-threshold=20`,
  `foodtech.health.scheduler-timeout-seconds=30`

### Ports (Application Layer)

- [x] T003 Extend `src/main/java/.../application/ports/output/OutboxRepositoryPort.java` — add method signature `long countByStatus(String status)` (no Spring imports, pure Java interface)

### Driven Adapters (Persistence)

- [x] T004 [P] Add derived query method `long countByStatus(String status)` to `src/main/java/.../infrastructure/persistence/repository/JpaOutboxRepository.java` (Spring Data JPA derives the query from the method name)

- [x] T005 Implement `countByStatus(String status)` in `src/main/java/.../infrastructure/persistence/adapter/OutboxRepositoryAdapter.java` — delegate to `jpaOutboxRepository.countByStatus(status)` and add `@Override` annotation (depends on T003, T004)

### Business Logic Infrastructure (Application Layer)

- [x] T006 [P] Create `src/main/java/.../infrastructure/config/HealthThresholdConfig.java` — annotate with `@ConfigurationProperties(prefix = "foodtech.health")`, add fields `int warnThreshold`, `int downThreshold`, `int schedulerTimeoutSeconds` with Lombok `@Data`; register with `@EnableConfigurationProperties` in the main configuration or the new `HealthConfig` class

- [x] T007 [P] Create `src/main/java/.../infrastructure/adapters/input/scheduler/SchedulerTracker.java` — annotate with `@Component`, hold `private final AtomicReference<LocalDateTime> lastProcessedAt = new AtomicReference<>()`, expose `void recordExecution()` (sets `LocalDateTime.now()`) and `Optional<LocalDateTime> getLastProcessedAt()` (returns from AtomicReference)

- [x] T008 Modify `src/main/java/.../infrastructure/adapters/input/scheduler/OutboxScheduler.java` — inject `SchedulerTracker` via constructor, call `schedulerTracker.recordExecution()` immediately after `processOutboxUseCase.processOutboxEvents()` completes without throwing (depends on T007)

**Checkpoint**: Foundation complete — all user story work can now begin.

---

## Phase 3: User Story 1 — Consultar estado global del worker (Priority: P1) 🎯 MVP

**Goal**: `GET /actuator/health` responds in < 500 ms with a global UP/WARN/DOWN
status that aggregates all component states.

**Independent Test**: Start the application and run
`curl -s -o /dev/null -w "%{http_code} %{time_total}" http://localhost:8081/actuator/health`
→ expect `200` in under 0.5 s. Response body MUST include a top-level `"status"` field.

### Implementation for User Story 1

- [x] T009 Create `src/main/java/.../application/service/OutboxHealthMetrics.java` — plain Java class (NO Spring annotations, NO framework imports), constructor receives `OutboxRepositoryPort outboxRepositoryPort`, `SchedulerTracker schedulerTracker`, `int warnThreshold`, `int downThreshold`, `int schedulerTimeoutSeconds`; implement `OutboxHealthSnapshot compute()` that queries counts and evaluates all state-transition rules from data-model.md (DOWN → WARN → UP priority order); define inner record or value class `OutboxHealthSnapshot(long pendingEvents, long failedEvents, LocalDateTime lastProcessedAt, String status, String reason)` (depends on T005, T007)

- [x] T010 Create `src/main/java/.../infrastructure/config/HealthConfig.java` — annotate with `@Configuration`, add `@EnableConfigurationProperties(HealthThresholdConfig.class)`, declare `@Bean OutboxHealthMetrics outboxHealthMetrics(OutboxRepositoryPort port, SchedulerTracker tracker, HealthThresholdConfig cfg)` that constructs the plain Java service with threshold values (depends on T006, T007, T009)

- [x] T011 Create `src/main/java/.../infrastructure/adapters/input/health/WorkerHealthIndicator.java` — extend `AbstractHealthIndicator`, inject `OutboxHealthMetrics` via constructor, override `doHealthCheck(Health.Builder builder)` to call `outboxHealthMetrics.compute()` and translate the snapshot status string to `Health.up()`, `Health.status("WARN")`, or `Health.down()` with all snapshot fields added as details via `builder.withDetail(...)` (depends on T009)

### Unit Tests for User Story 1

- [x] T012 [P] Write `src/test/java/.../infrastructure/adapters/input/scheduler/SchedulerTrackerTest.java` — no Spring context, no mocks needed; test: `recordExecution()` sets `lastProcessedAt` to a non-null value; `getLastProcessedAt()` returns `Optional.empty()` before first call; calling `recordExecution()` twice returns the most recent timestamp (depends on T007)

- [x] T013 [P] Write `src/test/java/.../infrastructure/adapters/input/health/WorkerHealthIndicatorTest.java` — no Spring context; mock `OutboxHealthMetrics`; test: when compute() returns status=UP → builder results in Health UP; when status=WARN → builder results in WARN status; when status=DOWN → builder results in DOWN status; details map includes `pendingEvents`, `failedEvents`, `lastProcessedAt` keys (depends on T009, T011)

**Checkpoint**: US1 independently functional — `GET /actuator/health` returns global status.
Run `./gradlew test --tests "*SchedulerTrackerTest" --tests "*WorkerHealthIndicatorTest"` → all pass, no Spring context started.

---

## Phase 4: User Story 2 — Ver detalle por componente (Priority: P2)

**Goal**: The health response body includes individual status entries for `db`,
`rabbit`, and `workerOutbox` with metrics: `pendingEvents`, `failedEvents`,
`lastProcessedAt`.

**Independent Test**: Query the endpoint and parse the JSON — `components.workerOutbox.details`
MUST include all three metric keys; `lastProcessedAt` may be null on fresh startup.

### Unit Tests for User Story 2

- [x] T014 [P] [US2] Write `src/test/java/.../application/service/OutboxHealthMetricsTest.java` (new class) — no Spring context; mock `OutboxRepositoryPort` and `SchedulerTracker`; cover component-detail scenarios: (a) all healthy — `pendingEvents=3`, `failedEvents=0`, `lastProcessedAt` non-null → status UP, reason null; (b) null `lastProcessedAt` (fresh startup, within timeout window) → status UP, reason contains "No events processed since last startup"; (c) `failedEvents=0`, `pendingEvents=0`, `lastProcessedAt` recent → snapshot status UP (depends on T009)

**Checkpoint**: US2 test coverage complete — `OutboxHealthMetrics` returns correct detail structure.

---

## Phase 5: User Story 3 — Alerta por eventos fallidos (Priority: P3)

**Goal**: `outbox` component transitions to WARN when `failedEvents >= warnThreshold`
and to DOWN when `failedEvents >= downThreshold`. Thresholds respect `>=` (inclusive).

**Independent Test**: Mock `countByStatus("FAILED")` returning 10 (warnThreshold default)
→ `compute()` returns WARN. Returning 20 → returns DOWN.

### Unit Tests for User Story 3

- [x] T015 [P] [US3] Add threshold test scenarios to `src/test/java/.../application/service/OutboxHealthMetricsTest.java` — mock `OutboxRepositoryPort.countByStatus("FAILED")`; test: (a) `failedEvents == warnThreshold (10)` → status WARN, reason mentions threshold; (b) `failedEvents == downThreshold (20)` → status DOWN, reason mentions critical threshold; (c) `failedEvents == downThreshold - 1 (19)` → status WARN (not DOWN); (d) `failedEvents < warnThreshold (9)` → status UP; (e) `downThreshold <= warnThreshold` misconfiguration → DOWN threshold takes precedence (depends on T009)

**Checkpoint**: US3 threshold logic verified — all 5 threshold boundary scenarios pass.

---

## Phase 6: User Story 4 — Detectar scheduler detenido (Priority: P4)

**Goal**: `outbox` component transitions to DOWN when `lastProcessedAt` is older than
`schedulerTimeoutSeconds`. A null `lastProcessedAt` on a fresh startup is UP (not DOWN).

**Independent Test**: Mock `SchedulerTracker.getLastProcessedAt()` returning
`Optional.of(LocalDateTime.now().minusSeconds(31))` → `compute()` returns DOWN.
Mock returning `Optional.empty()` → returns UP.

### Unit Tests for User Story 4

- [x] T016 [P] [US4] Add scheduler timeout scenarios to `src/test/java/.../application/service/OutboxHealthMetricsTest.java`; test: (a) `lastProcessedAt = now() - 31s` (> 30s timeout) → status DOWN, reason contains "Scheduler stalled" and elapsed seconds; (b) `lastProcessedAt = now() - 29s` (< 30s timeout) → status not affected by timeout; (c) `lastProcessedAt = null` (Optional.empty) → status UP, reason "No events processed since last startup"; (d) stale scheduler + failedEvents < warnThreshold → DOWN wins (scheduler stall takes priority per state-transition rules) (depends on T009)

**Checkpoint**: US4 scheduler detection verified — all 4 timeout scenarios pass.
Run `./gradlew test --tests "*OutboxHealthMetricsTest"` → all scenarios pass.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Validate the full test suite, confirm zero Spring context in unit tests,
and verify end-to-end with live infrastructure.

- [x] T017 [P] Run full unit test suite and confirm no Spring context starts for unit tests:
  `./gradlew test` — all existing tests plus T012–T016 MUST pass; verify with
  `grep -r "@SpringBootTest" src/test/.../service/ src/test/.../health/ src/test/.../scheduler/`
  → must return no results for the new test files

- [x] T018 Validate endpoint end-to-end using `src/docker-compose.yml` + SQL test data from `specs/001-worker-health-check/quickstart.md` — verify all 5 response scenarios: UP (clean start), WARN (failedEvents ≥ 10), DOWN (failedEvents ≥ 20), DOWN (scheduler stalled), null lastProcessedAt on fresh start

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (T001)
  └── Phase 2 (T002–T008) — all depend on T001
        ├── T002 [properties]   — no deps within phase
        ├── T003 [port]         — no deps within phase
        ├── T004 [P] [JPA repo] — no deps within phase
        ├── T005 [adapter]      — depends on T003, T004
        ├── T006 [P] [config]   — no deps within phase
        ├── T007 [P] [tracker]  — no deps within phase
        └── T008 [scheduler]    — depends on T007
              └── Phase 3 (T009–T013) — all depend on Phase 2
                    ├── T009 [OutboxHealthMetrics] — depends on T005, T007
                    ├── T010 [HealthConfig]         — depends on T006, T007, T009
                    ├── T011 [WorkerHealthIndicator]— depends on T009
                    ├── T012 [P] [SchedulerTrackerTest]       — depends on T007
                    └── T013 [P] [WorkerHealthIndicatorTest]  — depends on T009, T011
                          └── Phase 4 (T014) — depends on T009
                                └── Phase 5 (T015) — depends on T009
                                      └── Phase 6 (T016) — depends on T009
                                            └── Phase 7 (T017, T018)
```

### Within Each User Story

- All [P]-marked tasks within a phase share no file conflicts and can run in parallel
- Production class MUST be created before its unit test
- T009 (`OutboxHealthMetrics`) is the central dependency for all test phases (US2–US4)

### Parallel Opportunities

```bash
# Phase 2 — run these simultaneously after T001:
Task: "T002 — Configure application.properties"
Task: "T003 — Extend OutboxRepositoryPort"
Task: "T004 — Add countByStatus to JpaOutboxRepository"
Task: "T006 — Create HealthThresholdConfig"
Task: "T007 — Create SchedulerTracker"

# Phase 3 — run after T009 and T011 are done:
Task: "T012 — SchedulerTrackerTest"
Task: "T013 — WorkerHealthIndicatorTest"

# Phase 5–6 — run after T009 is done:
Task: "T015 — OutboxHealthMetricsTest threshold scenarios"
Task: "T016 — OutboxHealthMetricsTest scheduler scenarios"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: T001
2. Complete Phase 2: T002–T008
3. Complete Phase 3: T009–T013
4. **STOP and VALIDATE**: `curl http://localhost:8081/actuator/health` returns JSON with `"status"` field; both unit test classes pass
5. Demo to ops team — endpoint is live and Kubernetes probe-ready

### Incremental Delivery

1. **MVP**: Phases 1–3 → global status endpoint works (US1 ✅)
2. **+Detail tests**: Phase 4 → component metric coverage validated (US2 ✅)
3. **+Threshold tests**: Phase 5 → WARN/DOWN alert logic verified (US3 ✅)
4. **+Scheduler tests**: Phase 6 → stall detection verified (US4 ✅)
5. **Polish**: Phase 7 → full integration smoke test

### Key Constraints

- `OutboxHealthMetrics` (T009) MUST be a plain Java class with zero Spring imports
- `WorkerHealthIndicatorTest` and `OutboxHealthMetricsTest` MUST NOT start a Spring context
- `SchedulerTracker` state resets on restart — this is by design (documented in research.md)
- `countByStatus` on `JpaOutboxRepository` uses Spring Data derived query naming — no `@Query` annotation needed

---

## Notes

- [P] tasks = different files, no dependencies between them
- [USN] label maps each task to its user story for traceability
- Constitution gates verified in plan.md — all ✅
- Zero changes to `domain/` or existing use cases (`ProcessOutboxUseCase`, `ProcessEventUseCase`)
- `OutboxEvent` and `FoodEvent` domain models are untouched
- Commit recommended after each phase checkpoint
