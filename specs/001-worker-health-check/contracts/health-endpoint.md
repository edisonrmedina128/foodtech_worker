# Contract: Health Endpoint

**Feature**: `001-worker-health-check`
**Date**: 2026-04-06

## Endpoint

```
GET /actuator/health
```

**Authentication**: None (public within cluster network)
**Content-Type**: `application/json`

---

## Response: All Components Healthy

**HTTP Status**: `200 OK`

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP"
    },
    "rabbit": {
      "status": "UP"
    },
    "workerOutbox": {
      "status": "UP",
      "details": {
        "pendingEvents": 3,
        "failedEvents": 0,
        "lastProcessedAt": "2026-04-06T03:00:00"
      }
    }
  }
}
```

---

## Response: Outbox WARN (failedEvents ≥ warn-threshold)

**HTTP Status**: `200 OK`

```json
{
  "status": "WARN",
  "components": {
    "db": {
      "status": "UP"
    },
    "rabbit": {
      "status": "UP"
    },
    "workerOutbox": {
      "status": "WARN",
      "details": {
        "pendingEvents": 15,
        "failedEvents": 12,
        "lastProcessedAt": "2026-04-06T03:01:50",
        "reason": "failedEvents threshold exceeded: 12 >= 10"
      }
    }
  }
}
```

---

## Response: Outbox DOWN (failedEvents ≥ down-threshold)

**HTTP Status**: `503 Service Unavailable`

```json
{
  "status": "DOWN",
  "components": {
    "db": {
      "status": "UP"
    },
    "rabbit": {
      "status": "UP"
    },
    "workerOutbox": {
      "status": "DOWN",
      "details": {
        "pendingEvents": 45,
        "failedEvents": 22,
        "lastProcessedAt": "2026-04-06T03:01:50",
        "reason": "failedEvents critical threshold exceeded: 22 >= 20"
      }
    }
  }
}
```

---

## Response: Scheduler Stalled (lastProcessedAt stale)

**HTTP Status**: `503 Service Unavailable`

```json
{
  "status": "DOWN",
  "components": {
    "db": {
      "status": "UP"
    },
    "rabbit": {
      "status": "UP"
    },
    "workerOutbox": {
      "status": "DOWN",
      "details": {
        "pendingEvents": 8,
        "failedEvents": 2,
        "lastProcessedAt": "2026-04-06T02:50:00",
        "reason": "Scheduler stalled: last execution 71s ago (threshold: 30s)"
      }
    }
  }
}
```

---

## Response: Never Processed (lastProcessedAt is null)

**HTTP Status**: `200 OK`

```json
{
  "status": "UP",
  "components": {
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

> A null `lastProcessedAt` on a fresh startup is **not** an error state.
> The DOWN condition for a stalled scheduler only applies once the timeout
> window has elapsed from `startupTime` (when the scheduler should have run
> at least once).

---

## Response: Database Down

**HTTP Status**: `503 Service Unavailable`

```json
{
  "status": "DOWN",
  "components": {
    "db": {
      "status": "DOWN",
      "details": {
        "error": "Unable to acquire JDBC Connection"
      }
    },
    "rabbit": {
      "status": "UP"
    },
    "workerOutbox": {
      "status": "DOWN",
      "details": {
        "error": "Could not query outbox metrics: data source unavailable"
      }
    }
  }
}
```

> When the DB is down, `OutboxHealthMetrics.compute()` catches the exception
> and returns a DOWN status with an error detail rather than propagating the
> exception.

---

## Status Code Mapping

| Global Status | HTTP Code |
|--------------|-----------|
| `UP` | `200 OK` |
| `WARN` | `200 OK` |
| `DOWN` | `503 Service Unavailable` |

> Note: `WARN` returns 200 by default in Spring Boot Actuator. To change this,
> configure `management.endpoint.health.status.http-mapping.WARN=207` (optional,
> not required by the spec).

---

## Status Ordering Configuration

Add to `application.properties`:

```properties
management.endpoint.health.show-details=always
management.endpoint.health.status.order=DOWN,WARN,UP,UNKNOWN
```

This ensures the global status correctly reflects the worst component state.
