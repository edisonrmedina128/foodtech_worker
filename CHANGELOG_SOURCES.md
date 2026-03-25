# CHANGELOG_SOURCES.md
## Feature: Worker Health Check — foodtech_worker
### Autor: Edison Reinoso

Bitácora de investigación. Compara fuentes IA vs documentación oficial.
Sin código — diseño puro.

---

## Día 1 — Lunes 23/Mar/2026

### Feature identificada
Worker Health Check dentro del microservicio `foodtech_worker`.

**Problema detectado:** El worker procesa eventos del outbox cada 3 segundos
y los publica a RabbitMQ. Si algo falla internamente, no hay visibilidad —
el sistema falla silenciosamente sin que nadie lo sepa.

---

### Qué sugirió la IA
Usar Spring Boot Actuator (`/actuator/health`) sin configuración adicional.

Pendiente: investigar si el Actuator genérico cubre las necesidades
reales del dominio o si se necesita algo más específico.
