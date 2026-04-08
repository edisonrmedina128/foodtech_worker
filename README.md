# foodtech_worker

[![CI](https://github.com/edisonrmedina128/foodtech_worker/actions/workflows/ci.yml/badge.svg)](https://github.com/edisonrmedina128/foodtech_worker/actions/workflows/ci.yml)

Microservicio de background que procesa eventos del **Outbox Pattern** para garantizar la entrega confiable de mensajes a RabbitMQ dentro de la plataforma de pedidos de FoodTech.

---

## Feature activa: Worker Health Check (`001-worker-health-check`)

Expone `GET /actuator/health` con estado global UP / WARN / DOWN e indicadores de observabilidad por componente (`db`, `rabbit`, `workerOutbox`).

### Respuesta de ejemplo

```json
{
  "status": "UP",
  "components": {
    "workerOutbox": {
      "status": "UP",
      "details": {
        "pendingEvents": 3,
        "failedEvents": 0,
        "lastProcessedAt": "2026-04-08T20:00:00"
      }
    },
    "db":     { "status": "UP" },
    "rabbit": { "status": "UP" }
  }
}
```

### Umbrales configurables (`application.properties`)

| Propiedad | Default | Efecto |
|-----------|---------|--------|
| `foodtech.health.warn-threshold` | `10` | `failedEvents ≥ 10` → WARN |
| `foodtech.health.down-threshold` | `20` | `failedEvents ≥ 20` → DOWN |
| `foodtech.health.scheduler-timeout-seconds` | `30` | Sin ejecución > 30s → DOWN |

---

## Arquitectura

```
infrastructure/adapters/input/health/WorkerHealthIndicator   ← Driver Adapter
application/service/OutboxHealthMetrics                      ← Plain Java (sin Spring)
application/ports/output/SchedulerStatePort                  ← Puerto de salida
infrastructure/adapters/input/scheduler/SchedulerTracker     ← Implementación
infrastructure/config/HealthConfig + HealthThresholdConfig   ← @Configuration
```

> `OutboxHealthMetrics` no tiene `@Service`. Se instancia como `@Bean` en `HealthConfig` recibiendo los umbrales como argumentos de constructor — principio Hexagonal estricto.

---

## Tests

```bash
./gradlew test jacocoTestReport
```

- **24 pruebas unitarias** (Mockito, sin `@SpringBootTest`)
- Cobertura mínima configurada: **80 %** líneas y branches
- Reporte HTML: `build/reports/jacoco/test/html/index.html`

---

## Docker

```bash
docker build -t foodtech-worker:local .
docker run -p 8081:8081 foodtech-worker:local
```

---

## CI/CD (GitHub Actions)

| Job | Qué hace |
|-----|----------|
| `unit-tests` | `./gradlew test jacocoTestReport`, publica artefacto JaCoCo |
| `integration-tests` | Postgres 16 + RabbitMQ 3 como services, ejecuta tests de integración |
| `docker-build-scan` | Build Docker + Trivy HIGH/CRITICAL scan |

El pipeline bloquea merges a `main` y `develop` si cualquier job falla.

---

## GitFlow

```
main          ← releases (v1.0.0)
  └── develop ← integración continua
        └── 001-worker-health-check ← feature branch
```
