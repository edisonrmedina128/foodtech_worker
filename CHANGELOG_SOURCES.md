# CHANGELOG_SOURCES.md
## Feature: Worker Health Check — foodtech_worker
### Autor: Edison Reinoso

Bitácora de investigación. Compara fuentes IA vs documentación oficial.
Sin código — diseño puro.

---

## Dia 1 — Miercoles 25/Mar/2026

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

## Dia 2 — Jueves 26/Mar/2026

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

### Analisis critico — Respuesta simple (IA) vs Contrato granular (Humano)

| Criterio               | Respuesta simple (IA)        | Contrato granular (Humano)         |
|------------------------|------------------------------|------------------------------------|
| Diagnostico en fallo   | Imposible                    | Inmediato por componente           |
| Informacion de outbox  | No incluye                   | pendingEvents y failedEvents       |
| Estandar de referencia | Ninguno                      | IETF Health Check Draft            |
| Valor operacional      | Casi nulo                    | Alto                               |
| Complejidad            | Muy baja                     | Baja                               |

### Decision tomada — Dia 2

Usar el contrato granular con tres componentes: database, rabbitmq y outbox.

Justificacion tecnica: reutiliza los campos que ya existen en OutboxEntity
(status, attempts) sin agregar queries nuevos al sistema.

Justificacion de negocio: reduce el tiempo de diagnostico de un fallo
de minutos a segundos — el operador no necesita acceso a la infraestructura
para saber que esta fallando.

---

## Dia 3 — Viernes 27/Mar/2026

### Propuesta de la IA — Donde poner la logica del health check

La IA sugirió crear un HealthController que inyecte directamente
JpaOutboxRepository y RabbitTemplate para hacer las verificaciones.

Problema identificado: el proyecto ya usa arquitectura hexagonal —
los controllers nunca tocan infraestructura directamente. Inyectar
JPA y RabbitTemplate en un controller violaría el patron que
ya existe en ProcessOutboxUseCase y ProcessEventUseCase.

### Investigacion humana — Hexagonal Architecture aplicada al health check

Fuente 1 — Hexagonal Architecture (Alistair Cockburn, autor original)
https://alistair.cockburn.us/hexagonal-architecture/

Lo que encontre: el principio central es que el dominio no depende
de la infraestructura. Los adaptadores de entrada (controllers,
schedulers) solo hablan con puertos — interfaces del dominio.

Fuente 2 — Codigo propio del proyecto
ProcessOutboxUseCase ya usa OutboxRepositoryPort y EventPublisherPort
sin tocar JPA ni RabbitMQ directamente. El health check debe
seguir el mismo patron.

### Analisis critico — Controller directo (IA) vs Hexagonal (Humano)

| Criterio                  | Controller directo (IA)    | Hexagonal (Humano)              |
|---------------------------|----------------------------|---------------------------------|
| Consistencia del proyecto | Rompe el patron existente  | Respeta el patron existente     |
| Testabilidad              | Baja — acoplado a JPA      | Alta — puertos son interfaces   |
| Reutilizacion             | No                         | Usa puertos que ya existen      |
| Deuda tecnica             | Alta                       | Ninguna                         |

### Decision tomada — Dia 3

Crear WorkerHealthIndicator como adaptador de entrada que usa
los puertos existentes OutboxRepositoryPort y EventPublisherPort.
No se crean nuevas dependencias a infraestructura.


### Propuesta de la IA — Seguridad del endpoint

La IA sugirió exponer /actuator/health publicamente sin restricciones.

Problema identificado: exponer el estado interno del sistema sin
autenticacion revela informacion sensible — cuantos eventos estan
fallando, si RabbitMQ esta caido. Un atacante puede usar eso para
identificar ventanas de vulnerabilidad.

### Investigacion humana — Seguridad en health check endpoints

Fuente 1 — OWASP API Security Top 10: API3:2023
https://owasp.org/API-Security/editions/2023/en/0xa3-broken-object-property-level-authorization/

Lo que encontre: OWASP identifica la exposicion de propiedades
internas del sistema como riesgo de seguridad. El detalle del
health check (pendingEvents, failedEvents) no debe ser publico.

Fuente 2 — Spring Boot Actuator Security
https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.endpoints.security

Lo que encontre: Spring permite configurar acceso diferenciado.
Con management.endpoint.health.show-details=when-authorized
el status general es publico pero el detalle requiere autorizacion.

### Analisis critico — Endpoint publico (IA) vs Acceso controlado (Humano)

| Criterio               | Publico (IA)          | Controlado (Humano)              |
|------------------------|-----------------------|----------------------------------|
| Seguridad              | Riesgo OWASP API3     | Cumple minimo privilegio         |
| Status general         | Publico               | Publico                          |
| Detalle componentes    | Publico               | Solo usuarios autorizados        |
| Configuracion          | Ninguna               | Una linea en application.properties |
| Estandar referencia    | Ninguno               | OWASP API Security Top 10        |

### Historias de Usuario finales

HU-01
Como operador del sistema, quiero hacer GET /actuator/health y obtener
UP o DOWN de inmediato, para saber si el worker esta operativo sin
revisar logs.
Criterio de aceptacion: el endpoint responde en menos de 500ms.

HU-02
Como operador autenticado, quiero ver el estado de database, rabbitmq
y outbox por separado, para identificar exactamente que esta fallando.
Criterio de aceptacion: la respuesta incluye pendingEvents y failedEvents.

HU-03
Como operador, quiero que el health check muestre WARN si hay mas de
10 eventos en FAILED, para actuar antes de que el problema escale.
Criterio de aceptacion: si failedEvents >= 10 el status del outbox
cambia a WARN.
