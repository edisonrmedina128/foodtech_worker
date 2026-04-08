# BUSINESS_CONTEXT.md — foodtech_worker

**Sistema**: `foodtech_worker`
**Feature documentada**: Worker Health Check (`001-worker-health-check`)
**Versión**: 1.0.0
**Fecha**: 2026-04-08

---

## 1. Nombre y objetivo del sistema

`foodtech_worker` es un microservicio de background que procesa eventos del Outbox Pattern para garantizar la entrega confiable de mensajes a RabbitMQ dentro de la plataforma de pedidos de FoodTech.

**Objetivo de negocio**: Asegurar que ningún evento de dominio (orden creada, pago procesado, etc.) se pierda o quede sin entregarse al broker de mensajería, incluso ante fallos transitorios de red o reinicio de servicios.

---

## 2. Usuarios finales y sus necesidades

| Actor | Necesidad | Cómo la satisface esta feature |
|-------|-----------|-------------------------------|
| **Ingeniero de operaciones** | Saber si el worker está procesando correctamente sin revisar logs ni acceder a la BD | Endpoint `GET /actuator/health` con status global UP / WARN / DOWN en < 500 ms |
| **Kubernetes liveness/readiness probe** | Detectar si el pod debe reiniciarse o si puede recibir tráfico | El endpoint retorna HTTP 200 (UP/WARN) o 503 (DOWN); los probes usan el código HTTP directamente |
| **Plataforma de monitoring (Prometheus/Grafana/PagerDuty)** | Recibir alertas automáticas cuando hay acumulación de eventos fallidos o el scheduler se detiene | El endpoint incluye `pendingEvents`, `failedEvents` y `lastProcessedAt` como métricas observables |
| **On-call engineer** | Diagnosticar qué componente falló (BD, RabbitMQ o scheduler) sin acceso al entorno productivo | El detalle por componente (`db`, `rabbit`, `workerOutbox`) en el body de la respuesta |

---

## 3. Restricciones y supuestos del dominio

| Restricción / Supuesto | Justificación |
|-----------------------|---------------|
| `lastProcessedAt` se mantiene **en memoria**, no en BD | Evita un write en BD en cada ciclo de 3 segundos; se reinicia a `null` con cada restart (trade-off aceptado y documentado en `research.md`) |
| El endpoint es público **dentro de la red interna del cluster** | Los probes de Kubernetes no manejan tokens; la exposición externa es gestionada por network policy, no por la feature |
| Un `null` en `lastProcessedAt` en fresh startup **no es DOWN** | Un reinicio normal del worker no debe generar alertas de falso positivo; el DOWN solo aplica una vez que el timeout de 30s haya transcurrido sin actividad |
| Los thresholds (`warn=10`, `down=20`, `timeout=30s`) son **configurables sin redespliegue** | Permiten ajuste operativo según volumen de pedidos del día |
| Arquitectura Hexagonal estricta: **cero imports de Spring en `application/`** | `OutboxHealthMetrics` debe poder testearse sin Spring context; es un requisito de la constitución del proyecto |

---

## 4. KPIs de calidad del servicio

| KPI | Valor objetivo | Medición |
|-----|---------------|----------|
| **Latencia del endpoint** | < 500 ms en condiciones normales | `curl -w "%{time_total}"` o métrica Actuator |
| **Tiempo de detección de fallo** | ≤ 1 ciclo de polling (≤ 30 s desde que ocurre el fallo) | Definido en SC-002 de `spec.md` |
| **Cobertura de requisitos funcionales** | 100% (FR-001 a FR-011 cubiertos) | Ver `TEST_PLAN.md` sección 8 |
| **Cobertura de código** | ≥ 80% líneas, ≥ 75% branches | JaCoCo configurado en `build.gradle` |
| **Tests unitarios sin Spring context** | 100% de la suite unit | Verificado con `grep "@SpringBootTest"` → 0 resultados en clases nuevas |
| **Pipeline CI verde antes de merge** | Obligatorio | Branch protection en `main` y `develop` |

---

## 5. Contexto de arquitectura

```
┌─────────────────────────────────────────────────────┐
│                   foodtech_worker                    │
│                                                      │
│  domain/          application/         infra/        │
│  ─────────        ──────────────       ──────        │
│  OutboxEvent      OutboxHealthMetrics  WorkerHealthIndicator
│  FoodEvent        (plain Java)         SchedulerTracker
│                   OutboxRepositoryPort HealthThresholdConfig
│                   SchedulerStatePort   HealthConfig  │
│                                        JpaOutboxRepository
└─────────────────────────────────────────────────────┘
           ↓ consume                     ↑ expone
   PostgreSQL + RabbitMQ          GET /actuator/health
```

**Pattern de referencia**: Health Check API Pattern (microservices.io) + Spring Boot Actuator + Outbox Pattern.
