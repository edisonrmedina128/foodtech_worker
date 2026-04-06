# Feature Specification: Worker Health Check

**Feature Branch**: `001-worker-health-check`
**Created**: 2026-04-06
**Status**: Draft

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar estado global del worker (Priority: P1)

An operations engineer or automated probe (e.g., Kubernetes liveness/readiness)
queries the worker's health endpoint and immediately obtains a single consolidated
status (UP / WARN / DOWN) that reflects the worst state across all internal
components. No manual log inspection or database access is required.

**Why this priority**: This is the core deliverable — without a global status
response there is no health check at all. It unblocks Kubernetes probes and
provides the first layer of visibility that the team currently lacks entirely.

**Independent Test**: Send a GET request to the health endpoint and verify that
a response arrives in under 500 ms containing a top-level status field of UP,
WARN, or DOWN.

**Acceptance Scenarios**:

1. **Given** all internal components (database, RabbitMQ, outbox) are healthy,
   **When** the health endpoint is queried,
   **Then** the response status is UP and arrives in under 500 ms.
2. **Given** at least one component reports WARN and none reports DOWN,
   **When** the health endpoint is queried,
   **Then** the response global status is WARN.
3. **Given** at least one component reports DOWN,
   **When** the health endpoint is queried,
   **Then** the response global status is DOWN regardless of other components.
4. **Given** the health endpoint is queried,
   **When** the request completes,
   **Then** no authentication or authorisation token is required (public endpoint).

---

### User Story 2 - Ver detalle por componente (Priority: P2)

An operations engineer queries the health endpoint and can see the individual
status of each critical subsystem (database, RabbitMQ, outbox) along with
outbox-specific metrics, without needing to access internal systems directly.

**Why this priority**: The global status tells *that* something is wrong; the
component detail tells *what* is wrong and accelerates Mean Time To Diagnosis.

**Independent Test**: Query the health endpoint while the database is healthy
and verify the response body includes a `database` component entry with status
UP. Repeat substituting RabbitMQ and outbox components.

**Acceptance Scenarios**:

1. **Given** the worker is running normally,
   **When** the health endpoint is queried,
   **Then** the response body includes a `database` entry with its status.
2. **Given** the worker is running normally,
   **When** the health endpoint is queried,
   **Then** the response body includes a `rabbitmq` entry with its status.
3. **Given** the worker is running normally,
   **When** the health endpoint is queried,
   **Then** the response body includes an `outbox` entry with status and the
   metrics: `pendingEvents` (count), `failedEvents` (count), and
   `lastProcessedAt` (timestamp or null).
4. **Given** `lastProcessedAt` is null (worker has never processed events),
   **When** the health endpoint is queried,
   **Then** the `outbox` component explicitly indicates that no events have
   been processed yet.

---

### User Story 3 - Alerta por eventos fallidos con umbrales configurables (Priority: P3)

An operations team configures thresholds for failed outbox events via
application properties, and the health endpoint automatically reflects WARN
or DOWN when those thresholds are breached, enabling monitoring tools and
on-call alerts to trigger without manual inspection.

**Why this priority**: The thresholds turn passive visibility into actionable
alerting. Without them the team still needs to interpret raw counts manually.

**Independent Test**: Set `warn-threshold=10` and `down-threshold=20`, simulate
10 failed events, query the health endpoint, and verify global status is WARN.
Then simulate 20 failed events and verify global status is DOWN.

**Acceptance Scenarios**:

1. **Given** `failedEvents` count reaches the configured warn threshold,
   **When** the health endpoint is queried,
   **Then** the `outbox` component status is WARN and the global status is at
   least WARN.
2. **Given** `failedEvents` count reaches the configured down threshold,
   **When** the health endpoint is queried,
   **Then** the `outbox` component status is DOWN and the global status is DOWN.
3. **Given** the warn and down thresholds are changed in configuration without
   redeploying,
   **When** the health endpoint is queried after restart,
   **Then** the new thresholds are respected.
4. **Given** `failedEvents` is below the warn threshold,
   **When** the health endpoint is queried,
   **Then** the `outbox` component status reflects UP (not WARN or DOWN).

---

### User Story 4 - Detectar scheduler detenido (Priority: P4)

An operations engineer or automated probe detects that the outbox scheduler
has stalled (not processed events within the expected window) and receives a
DOWN status immediately upon querying the health endpoint, without waiting for
event accumulation.

**Why this priority**: A stopped scheduler is a silent failure — events
accumulate but no errors are thrown. Early detection prevents billing backlogs.

**Independent Test**: Stop the scheduler, wait longer than the configured
timeout (default 30 s), query the health endpoint, and verify global status is
DOWN with the `outbox` component citing a stale `lastProcessedAt`.

**Acceptance Scenarios**:

1. **Given** `lastProcessedAt` is older than the configured scheduler timeout,
   **When** the health endpoint is queried,
   **Then** the `outbox` component status is DOWN and the global status is DOWN.
2. **Given** `lastProcessedAt` is null (worker restarted, no events processed),
   **When** the health endpoint is queried,
   **Then** the response clearly indicates no processing has occurred yet
   (status is not DOWN due to null alone — a null value only triggers the
   timeout once the expected first-processing window has elapsed, or the
   implementation treats null as never-processed and marks it separately).
3. **Given** the scheduler processes an event successfully,
   **When** the health endpoint is queried within the timeout window,
   **Then** the `outbox` component does not report a stale scheduler.

---

### Edge Cases

- What happens when the database is unreachable at startup before any event is
  processed? (`lastProcessedAt` is null AND database is DOWN simultaneously.)
- What happens if the configured `down-threshold` is lower than or equal to
  `warn-threshold`? The system MUST treat this as a misconfiguration and log
  a startup warning; the higher threshold takes precedence.
- What happens when `failedEvents` equals exactly the warn threshold (boundary)?
  WARN MUST be triggered at `>=` the threshold value.
- What happens when RabbitMQ is temporarily unreachable but recovers before the
  next health check poll? The status MUST reflect the state at query time.
- What happens when the health endpoint itself is queried during a high-load
  spike? The check MUST not add significant overhead to outbox processing.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST expose a health status endpoint that is publicly
  accessible without authentication.
- **FR-002**: The endpoint MUST respond in under 500 ms under normal operating
  conditions.
- **FR-003**: The endpoint MUST return a global status of UP, WARN, or DOWN,
  derived as the worst status across all monitored components.
- **FR-004**: The endpoint MUST return individual status for these components:
  `database`, `rabbitmq`, and `outbox`.
- **FR-005**: The `outbox` component MUST include metrics: `pendingEvents`
  (integer), `failedEvents` (integer), and `lastProcessedAt`
  (ISO-8601 timestamp or null).
- **FR-006**: The system MUST transition `outbox` status to WARN when
  `failedEvents >= warn-threshold` (default: 10).
- **FR-007**: The system MUST transition `outbox` status to DOWN when
  `failedEvents >= down-threshold` (default: 20).
- **FR-008**: The system MUST transition `outbox` status to DOWN when
  `lastProcessedAt` is older than `scheduler-timeout` seconds (default: 30).
- **FR-009**: All thresholds MUST be configurable via external application
  properties without code changes.
- **FR-010**: The `lastProcessedAt` timestamp MUST be maintained in memory
  by the scheduler and updated on every successful outbox processing cycle.
- **FR-011**: If `lastProcessedAt` is null, the response MUST explicitly
  indicate that no events have been processed since the last startup.

### Key Entities

- **WorkerHealthStatus**: Consolidated view of the worker's health. Contains a
  global status (UP/WARN/DOWN) and a map of component-level statuses.
- **OutboxHealthDetail**: Component-specific health detail for the outbox.
  Contains `status`, `pendingEvents`, `failedEvents`, and `lastProcessedAt`.
- **HealthThresholdConfig**: Configuration values for health alerting:
  `warnThreshold`, `downThreshold`, `schedulerTimeoutSeconds`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Operations engineers obtain the worker's global health status in
  under 500 ms with a single HTTP request, eliminating the need to access logs
  or databases manually.
- **SC-002**: 100% of internal component failures (database outage, RabbitMQ
  outage, scheduler stall) are reflected in the health endpoint response within
  one polling cycle (≤ 30 seconds of the failure occurring).
- **SC-003**: Failed-event accumulation beyond configured thresholds triggers
  a WARN or DOWN response on the next health check, enabling monitoring tools
  to alert the on-call team before billing backlogs exceed recovery SLAs.
- **SC-004**: Threshold values can be changed and take effect after a restart
  with zero code changes, reducing configuration change lead time to under
  5 minutes (property update + restart).
- **SC-005**: The `outbox` component response includes all three metrics
  (`pendingEvents`, `failedEvents`, `lastProcessedAt`) in every response,
  providing sufficient context for first-responders to diagnose issues without
  additional tooling.

## Assumptions

- The health endpoint will be consumed by Kubernetes liveness/readiness probes
  and by the operations team's monitoring platform; no human browser UI is
  required.
- `lastProcessedAt` is held in memory only; it resets to null on worker
  restart. This is an accepted trade-off to avoid write overhead on the
  database during every 3-second processing cycle.
- The `database` and `rabbitmq` component statuses are provided by existing
  built-in health contributors available in the project's monitoring framework;
  this feature adds only the custom `outbox` contributor.
- A null `lastProcessedAt` on a freshly started worker is treated as
  informational (not immediately DOWN) to avoid false alerts on normal restarts;
  the DOWN condition only triggers once the scheduler timeout window has elapsed
  without a successful run.
- Threshold defaults (`warn=10`, `down=20`, `scheduler-timeout=30s`) apply when
  no explicit configuration is provided.
- The endpoint is accessible within the internal Kubernetes cluster network;
  external internet exposure is managed by network policy, not by this feature.
