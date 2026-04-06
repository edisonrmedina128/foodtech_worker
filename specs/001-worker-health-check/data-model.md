# Data Model: Worker Health Check

**Feature**: `001-worker-health-check`
**Date**: 2026-04-06

## Existing Entities (No Changes)

### `OutboxEvent` (domain model — unchanged)

| Field | Type | Notes |
|-------|------|-------|
| `id` | `UUID` | Primary key |
| `aggregateType` | `String` | |
| `aggregateId` | `String` | |
| `eventType` | `String` | |
| `payload` | `String` | JSON content |
| `status` | `String` | `NEW` \| `SENT` \| `FAILED` |
| `attempts` | `Integer` | |
| `nextRetryAt` | `LocalDateTime` | nullable |
| `createdAt` | `LocalDateTime` | |
| `sentAt` | `LocalDateTime` | nullable |
| `lastError` | `String` | nullable |

> Status values relevant to health metrics:
> - `NEW` → counted as **pendingEvents**
> - `FAILED` → counted as **failedEvents**

---

## New Conceptual Entities (Application Layer — No DB Tables)

### `OutboxHealthSnapshot`

An in-memory value object representing a point-in-time capture of the outbox
state. Produced by `OutboxHealthMetrics.compute()`.

| Field | Type | Description |
|-------|------|-------------|
| `pendingEvents` | `long` | Count of events with status `NEW` |
| `failedEvents` | `long` | Count of events with status `FAILED` |
| `lastProcessedAt` | `LocalDateTime` (nullable) | Timestamp of last successful scheduler cycle |
| `healthStatus` | `HealthStatus` enum (`UP` / `WARN` / `DOWN`) | Computed result |
| `reason` | `String` (nullable) | Human-readable reason when not UP |

**State transition rules**:

```
failedEvents >= downThreshold                     → DOWN  ("failedEvents threshold exceeded")
lastProcessedAt != null
  AND now - lastProcessedAt > schedulerTimeout    → DOWN  ("scheduler stalled")
failedEvents >= warnThreshold                     → WARN  ("failedEvents approaching limit")
otherwise                                         → UP
```

> Rule evaluation order: DOWN conditions are checked first; the first match wins.

---

### `HealthThresholdConfig` (infrastructure configuration)

Bound to `@ConfigurationProperties(prefix = "foodtech.health")`.
Passed as constructor arguments to `OutboxHealthMetrics`.

| Property | Default | Description |
|----------|---------|-------------|
| `foodtech.health.warn-threshold` | `10` | `failedEvents` count that triggers WARN |
| `foodtech.health.down-threshold` | `20` | `failedEvents` count that triggers DOWN |
| `foodtech.health.scheduler-timeout-seconds` | `30` | Seconds since `lastProcessedAt` before DOWN |

**Validation constraint**: `down-threshold` MUST be greater than `warn-threshold`.
If violated, application startup logs a warning and `down-threshold` is treated as
`warn-threshold + 1`.

---

### `SchedulerTracker` (infrastructure component — in-memory state)

Not a JPA entity. Spring `@Component` holding runtime state.

| Field | Type | Description |
|-------|------|-------------|
| `lastProcessedAt` | `AtomicReference<LocalDateTime>` | Updated by `OutboxScheduler` after each successful cycle |

**Write**: `OutboxScheduler.processOutbox()` calls `schedulerTracker.recordExecution()` after a successful `processOutboxUseCase.processOutboxEvents()` call.

**Read**: `OutboxHealthMetrics.compute()` calls `schedulerTracker.getLastProcessedAt()`.

---

## Port Extension

### `OutboxRepositoryPort` (modified)

New method added to the existing port interface:

```
long countByStatus(String status)
```

- Called with `"NEW"` → returns `pendingEvents`
- Called with `"FAILED"` → returns `failedEvents`
- Implemented in `OutboxRepositoryAdapter` via a new JPA derived query on `JpaOutboxRepository`

---

## No Database Schema Changes

All health metrics are derived from the existing `outbox_event` table using
count queries on the `status` column. No migrations are required.
