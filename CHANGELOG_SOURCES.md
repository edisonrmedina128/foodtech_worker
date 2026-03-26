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

---

### Investigacion humana — Spring HealthIndicator vs Actuator default

Luego de que la IA propuso usar el Actuator generico, investigue si
realmente cubre las necesidades del worker.

Fuente 1 — Documentacion oficial Spring Boot Actuator
https://docs.spring.io/spring-boot/reference/actuator/endpoints.html

Lo que encontre: Actuator expone indicadores genericos por defecto —
estado del disco, ping, conexion a base de datos. No tiene conocimiento
del dominio. No sabe que existe un OutboxScheduler, no puede contar
eventos en estado FAILED, no distingue entre NEW y SENT.

Fuente 2 — Spring Boot Custom HealthIndicator
https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.health.writing-custom-health-indicators

Lo que encontre: Spring permite implementar la interfaz HealthIndicator
para crear indicadores propios que si entienden el dominio. El metodo
health() devuelve un objeto Health con status y detalles personalizados.
Se integra automaticamente con /actuator/health sin configuracion adicional.

Fuente 3 — Health Check API Pattern (Chris Richardson)
https://microservices.io/patterns/observability/health-check-api.html

Lo que encontre: el patron establece que un health check debe reportar
el estado de todas las dependencias criticas del servicio, no solo si
el proceso esta vivo. Para el worker eso significa verificar: scheduler,
base de datos y RabbitMQ.

Conclusion: el Actuator generico no es suficiente. Se necesita un
HealthIndicator propio que consulte el outbox y verifique el estado real
del worker.

---

### Analisis critico — Actuator generico vs HealthIndicator propio

| Criterio              | Actuator generico (IA)          | HealthIndicator propio (Humano)    |
|-----------------------|---------------------------------|------------------------------------|
| Conocimiento dominio  | Ninguno                         | Total — sabe del outbox y RabbitMQ |
| Eventos FAILED        | No los ve                       | Los cuenta directamente            |
| Eventos pendientes    | No los ve                       | Consulta OutboxRepositoryPort      |
| Valor para operador   | Bajo — solo dice UP o DOWN      | Alto — diagnostico granular        |
| Configuracion extra   | Ninguna                         | Baja — implementar una interfaz    |

### Decision tomada

Implementar un HealthIndicator propio integrado con Spring Boot Actuator.

Justificacion tecnica: la interfaz HealthIndicator de Spring permite
agregar conocimiento del dominio sin salirse del ecosistema oficial.
Reutiliza los puertos existentes — OutboxRepositoryPort y EventPublisherPort
— sin crear nuevas dependencias.

Justificacion de negocio: el operador puede detectar en segundos si el
worker esta acumulando eventos fallidos, sin necesidad de acceder a la
base de datos ni revisar logs manualmente.

---

## Dia 2 — Viernes 27/Mar/2026

### Propuesta de la IA — Contrato de respuesta

La IA propuso una respuesta minima para el endpoint:

  { "status": "UP" }

Argumento de la IA: con eso es suficiente para saber si el servicio
esta vivo o no.

Problema identificado: si el status es DOWN el operador no sabe que
fallo. No distingue si es RabbitMQ, la base de datos o el outbox.
Una respuesta de un solo campo no permite diagnosticar nada.

### Investigacion humana — Contrato de respuesta granular

Investigue como deberia verse una respuesta de health check bien diseñada.

Fuente 1 — IETF Draft: Health Check Response Format for HTTP APIs
https://inadarei.github.io/rfc-health-check/

Lo que encontre: el draft define un formato estandar donde la respuesta
incluye un objeto "checks" con el estado de cada componente por separado.
Cada componente reporta su propio status independientemente.

Fuente 2 — Codigo propio: OutboxEntity
El proyecto ya tiene los campos necesarios — status (NEW, SENT, FAILED)
y attempts — para construir una respuesta significativa sin queries adicionales.

Contrato disenado basado en la investigacion:

  {
    "status": "UP",
    "components": {
      "database": { "status": "UP" },
      "rabbitmq": { "status": "UP" },
      "outbox": {
        "status": "UP",
        "pendingEvents": 2,
        "failedEvents": 0
      }
    }
  }

Con este contrato el operador sabe exactamente que esta fallando
sin necesidad de abrir la base de datos ni revisar logs.
