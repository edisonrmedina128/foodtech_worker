# Quickstart: Worker Health Check

**Feature**: `001-worker-health-check`
**Date**: 2026-04-06

## Prerequisites

- Docker running (for PostgreSQL + RabbitMQ)
- `./gradlew build` passes
- Application started on port `8081`

---

## Step 1 — Start the infrastructure

```bash
cd src
docker-compose up -d
```

Verify containers are healthy:

```bash
docker ps
# Expected: foodtech_postgres and foodtech_rabbitmq are "Up"
```

---

## Step 2 — Start the application

```bash
./gradlew bootRun
```

Wait for the log line:

```
Started FoodtechWorkerApplication in X.XXX seconds
```

---

## Step 3 — Verify the health endpoint responds

```bash
curl -s http://localhost:8081/actuator/health | python -m json.tool
```

**Expected** (clean start, no events):

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "rabbit": { "status": "UP" },
    "workerOutbox": {
      "status": "UP",
      "details": {
        "pendingEvents": 0,
        "failedEvents": 0,
        "lastProcessedAt": null,
        "reason": "No events processed since last startup"
      }
    }
  }
}
```

---

## Step 4 — Verify WARN threshold

Insert 10+ FAILED events into the database:

```sql
INSERT INTO outbox_event (id, aggregate_type, aggregate_id, event_type, payload, status, attempts, created_at)
SELECT gen_random_uuid(), 'ORDER', gen_random_uuid()::text, 'ORDER_PLACED',
       '{"test":true}'::jsonb, 'FAILED', 10, NOW()
FROM generate_series(1, 12);
```

Query the health endpoint:

```bash
curl -s http://localhost:8081/actuator/health | python -m json.tool
```

**Expected**: `"status": "WARN"` at global level and on `workerOutbox`.

---

## Step 5 — Verify DOWN threshold

Insert 8 more FAILED events (total >= 20):

```sql
INSERT INTO outbox_event (id, aggregate_type, aggregate_id, event_type, payload, status, attempts, created_at)
SELECT gen_random_uuid(), 'ORDER', gen_random_uuid()::text, 'ORDER_PLACED',
       '{"test":true}'::jsonb, 'FAILED', 10, NOW()
FROM generate_series(1, 8);
```

**Expected**: `"status": "DOWN"` and HTTP `503`.

---

## Step 6 — Verify scheduler stall detection

Stop the scheduler without stopping the app (for test purposes, you can
temporarily disable it via the properties or reduce the timeout to 5 seconds
in `application.properties` and wait):

```properties
foodtech.health.scheduler-timeout-seconds=5
```

Wait 6 seconds after the last scheduler cycle, then:

```bash
curl -s http://localhost:8081/actuator/health | python -m json.tool
```

**Expected**: `workerOutbox.status: "DOWN"` with reason mentioning
"Scheduler stalled".

---

## Step 7 — Verify configuration takes effect

Change `application.properties`:

```properties
foodtech.health.warn-threshold=5
foodtech.health.down-threshold=10
```

Restart the application and verify with 6 FAILED events → WARN is now triggered.

---

## Cleanup

```bash
DELETE FROM outbox_event WHERE status = 'FAILED';
```

---

## Unit Test Verification

Run only the unit tests for this feature (no containers required):

```bash
./gradlew test --tests "*.OutboxHealthMetricsTest"
./gradlew test --tests "*.WorkerHealthIndicatorTest"
./gradlew test --tests "*.SchedulerTrackerTest"
```

All three test classes MUST pass with zero Spring context startup.
