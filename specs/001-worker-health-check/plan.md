# Implementation Plan: Worker Health Check

**Branch**: `001-worker-health-check` | **Date**: 2026-04-06 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/001-worker-health-check/spec.md`

## Summary

The `foodtech_worker` currently fails silently when RabbitMQ, the database, or
the outbox scheduler stops working. This feature adds a `/actuator/health`
endpoint that exposes a consolidated UP/WARN/DOWN status for all three
subsystems, including outbox-specific metrics (`pendingEvents`, `failedEvents`,
`lastProcessedAt`) and configurable alerting thresholds.

The design extends the existing hexagonal architecture with minimal surgery:
one new output-port method, one application-layer service, one driver adapter
(HealthIndicator), one scheduler-state tracker, and a properties configuration
class. Zero changes to domain models or existing use cases.

## Technical Context

**Language/Version**: Java 17
**Primary Dependencies**: Spring Boot 3.5.11 · Spring Boot Actuator (new) ·
  Spring Data JPA · Spring AMQP · Lombok · JUnit 5 · Mockito
**Storage**: PostgreSQL 16 (existing `outbox_event` table — no schema changes)
**Testing**: JUnit 5 + Mockito (unit); no Spring context in unit tests
**Target Platform**: Linux server (Kubernetes pod)
**Project Type**: Spring Boot microservice
**Performance Goals**: Health endpoint response < 500 ms under normal load
**Constraints**: `lastProcessedAt` held in memory only (no DB write per cycle);
  thresholds externalised via `application.properties`
**Scale/Scope**: Single microservice; 6 new classes + 3 modified files

## Constitution Check

*GATE: Must pass before implementation. Re-checked after Phase 1 design.*

| Principle | Check | Status |
|-----------|-------|--------|
| **I — Hexagonal Architecture** | `WorkerHealthIndicator` lives in `infrastructure/adapters/input/health/` (Driver Adapter). `OutboxHealthMetrics` lives in `application/service/` (no framework imports). `OutboxRepositoryPort` extension stays in `application/ports/output/`. | ✅ PASS |
| **I — No infra in domain/application** | `OutboxHealthMetrics` is a plain Java class — no `@Service`, no Spring imports. Instantiated as `@Bean` in an infrastructure `@Configuration` class, receiving threshold values as constructor args. | ✅ PASS |
| **II — SRP** | `SchedulerTracker` only tracks last-processed timestamp. `OutboxHealthMetrics` only computes health status from metrics + thresholds. `WorkerHealthIndicator` only bridges the actuator framework to the application service. | ✅ PASS |
| **II — No AI code without review** | All code reviewed and understood by developer before merge. | ✅ PASS |
| **III — Unit tests, no Spring context** | `WorkerHealthIndicatorTest`, `OutboxHealthMetricsTest`, `SchedulerTrackerTest` — all use Mockito only, no `@SpringBootTest`. | ✅ PASS |
| **III — Edge cases covered** | Tests cover: boundary thresholds (exactly at warn/down), null `lastProcessedAt`, stale timestamp, misconfigured thresholds (down ≤ warn). | ✅ PASS |
| **IV — Config externalised** | All thresholds via `foodtech.health.*` properties. `management.endpoint.health.show-details=always` in properties file. | ✅ PASS |
| **IV — Outbox Pattern preserved** | No direct RabbitMQ publish added; existing outbox flow unchanged. | ✅ PASS |

**POST-DESIGN RE-CHECK**: ✅ All gates pass. No complexity violations.

## Project Structure

### Documentation (this feature)

```text
specs/001-worker-health-check/
├── plan.md              ← this file
├── research.md          ← Phase 0 output
├── data-model.md        ← Phase 1 output
├── quickstart.md        ← Phase 1 output
├── contracts/
│   └── health-endpoint.md   ← Phase 1 output
└── tasks.md             ← Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
src/main/java/com/foodtech/kitchen/worker/foodtech_worker/
├── application/
│   ├── ports/output/
│   │   └── OutboxRepositoryPort.java        ← MODIFIED: add countByStatus(String)
│   └── service/
│       └── OutboxHealthMetrics.java         ← NEW (plain Java, no Spring imports)
├── domain/                                  ← NO CHANGES
└── infrastructure/
    ├── adapters/input/
    │   ├── health/
    │   │   └── WorkerHealthIndicator.java   ← NEW (Driver Adapter)
    │   └── scheduler/
    │       ├── OutboxScheduler.java         ← MODIFIED: inject SchedulerTracker
    │       └── SchedulerTracker.java        ← NEW (@Component, AtomicReference)
    ├── config/
    │   └── HealthConfig.java                ← NEW (@ConfigurationProperties + @Bean)
    └── persistence/
        ├── adapter/
        │   └── OutboxRepositoryAdapter.java ← MODIFIED: implement countByStatus
        └── repository/
            └── JpaOutboxRepository.java     ← MODIFIED: add countByStatus query

src/test/java/com/foodtech/kitchen/worker/foodtech_worker/
└── application/service/
    └── OutboxHealthMetricsTest.java         ← NEW
└── infrastructure/adapters/input/
    ├── health/
    │   └── WorkerHealthIndicatorTest.java   ← NEW
    └── scheduler/
        └── SchedulerTrackerTest.java        ← NEW

build.gradle                                 ← MODIFIED: add actuator dependency
src/main/resources/application.properties   ← MODIFIED: add health properties
```

**Structure Decision**: Single Spring Boot project; hexagonal layer boundaries
strictly maintained. New classes follow the existing package naming convention.

## Complexity Tracking

> No constitution violations — table not required.
