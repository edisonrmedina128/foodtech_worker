# CHANGELOG_SOURCES.md

## Feature: Worker Health Check — foodtech_worker

## 1. PRD (Product Requirements Document)

- Se investigó cómo estructurar un documento de requisitos técnicos para la feature de health check.
- Se decidió usar Arquitectura Hexagonal y patrones de observabilidad de Spring Boot Actuator.

### a) Humano
1. https://alistair.cockburn.us/hexagonal-architecture/
2. https://microservices.io/patterns/observability/health-check-api.html

### b) IA
- Proporcionó una estructura base para organizar el documento.

---

## 2. Planteamiento del Problema

- El worker procesa eventos cada 3 segundos pero si falla nadie se entera.
- Si el scheduler se detiene, RabbitMQ se cae o la base de datos no responde, no existe ningún mecanismo de alerta.
- Impacto: los eventos se acumulan sin detección, interrumpiendo la facturación.

### a) Humano
1. https://microservices.io/patterns/observability/health-check-api.html

### b) IA
- Generó preguntas para profundizar en el problema: "¿Qué pasa si el scheduler se detiene?", "¿Cuántos eventos fallidos son aceptables?"

---

## 3. Historias de Usuario

- Se definieron HU con criterios de aceptación claros para validar el desarrollo.
- Se priorizaron las HU que generan mayor valor operativo.

### a) Humano
1. https://www.atlassian.com/es/agile/project-management/user-stories
2. https://www.atlassian.com/es/work-management/project-management/acceptance-criteria

### b) IA
- Proporcionó HU enfocadas en verificar si el servicio está operativo.

---

## 4. Spring Boot Actuator

- Se leyó la documentación oficial para entender las capacidades del componente por defecto.
- Limitaciones identificadas: no conoce el dominio del worker, no sabe qué es un OutboxScheduler, no puede contar eventos FAILED ni distinguir entre NEW y SENT.

### a) Humano
1. https://docs.spring.io/spring-boot/reference/actuator/endpoints.html
2. https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.health.writing-custom-health-indicators
3. https://www.baeldung.com/spring-boot-health-indicators

### b) IA
1. https://learn.microsoft.com/en-us/azure/role-based-access-control/overview
2. *(Alucinación)* https://www.atlassian.com/work-management/product-management/product-requirements
- Propuso usar el Actuator por defecto.

---

## 5. Custom HealthIndicator

- Se investigó cómo extender el Actuator con un HealthIndicator personalizado.
- Spring permite implementar la interfaz HealthIndicator para agregar lógica de negocio al health check.

### a) Humano
1. https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.health.writing-custom-health-indicators
2. https://www.baeldung.com/spring-boot-health-indicators
3. https://docs.spring.io/spring-boot/api/java/org/springframework/boot/actuate/health/AbstractHealthIndicator.html

### b) IA
- No propuso un indicador personalizado, se enfocó en la solución genérica del Actuator.

---

## 6. Arquitectura Hexagonal

- Se estudió cómo integrar el health check respetando la arquitectura existente del proyecto.
- Principio: el dominio no depende de la infraestructura. La solución debe usar los puertos existentes.

### a) Humano
1. https://alistair.cockburn.us/hexagonal-architecture/
2. https://betweendata.io/posts/ports-and-adapters-spring/

### b) IA
- Propuso acceder directamente a JPA y RabbitTemplate desde un Controller.

---

## 7. Contrato de Respuesta

- Se buscó un estándar que permita diagnóstico por componente.
- El estándar IETF draft-inadarei-api-health-check define que cada componente reporta su status independientemente.

### a) Humano
1. https://tools.ietf.org/id/draft-inadarei-api-health-check-06.html

### b) IA
- Propuso una respuesta mínima con solo status global.

---

## 8. Seguridad del Endpoint

- Se investigó cómo proteger el endpoint para evitar filtrar información sensible.
- OWASP API3:2023 identifica la exposición de propiedades internas como riesgo de seguridad.

### a) Humano
1. https://owasp.org/API-Security/editions/2023/en/0xa3-broken-object-property-level-authorization/
2. https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.endpoints.security

### b) IA
- No propuso medidas de seguridad para el endpoint.

---

## 9. Diseño de Software

- Se documentó la solución: WorkerHealthIndicator como Driver Adapter y HealthCheckUseCase como Application Service.
- Componentes reutilizados: OutboxRepositoryPort, EventPublisherPort y OutboxEntity.

### a) Humano
1. https://alistair.cockburn.us/hexagonal-architecture/

### b) IA
- Propuso un diseño basado en Controllers que acceden directamente a infraestructura.

---

## 10. Modelado de Estados

- Se definió que failedEvents >= 10 debe mostrar WARN, failedEvents >= 20 debe mostrar DOWN.
- Se consideró incluir lastProcessedAt para detectar si el scheduler se detuvo.

### a) Humano
1. https://www.lucidchart.com/pages/es/diagrama-de-maquina-de-estados

### b) IA
- No propuso un modelo de estados específico para los componentes.

---

## 11. Decisiones Técnicas (ADR)

**Decisión 1:** HealthIndicator propio en lugar de Actuator genérico
- Justificación: Permite contar eventos failed y dar diagnóstico granular.

**Decisión 2:** Contrato de respuesta granular basado en IETF
- Justificación: Permite diagnosticar qué componente falló sin revisar logs.

**Decisión 3:** Usar puertos existentes en lugar de inyectar infraestructura
- Justificación: Mantiene consistencia con la arquitectura hexagonal.

**Decisión 4:** Seguridad show-details=when-authorized
- Justificación: Previene exposición de información sensible.

### a) Humano
1. https://alistair.cockburn.us/hexagonal-architecture/
2. https://microservices.io/patterns/observability/health-check-api.html

### b) IA
- Las propuestas de la IA fueron reevaluadas y ajustadas con la investigación humana.

---

## 12. Análisis de Riesgos

**Riesgo de seguridad:** Exponer el endpoint públicamente revela información sobre la infraestructura.

**Riesgo de disponibilidad:** Si el scheduler se detiene, los eventos se acumulan sin detección.

**Riesgo de consistencia:** Un health check que reporta UP cuando el worker está caído genera falsa confianza.

**Riesgo de rendimiento:** Queries lentas podrían hacer del endpoint un cuello de botella.

**Riesgo de cascada:** Un componente caído podría afectar la capacidad del health check de responder.

### a) Humano
1. https://owasp.org/www-project-top-ten/

### b) IA
- No se identificaron riesgos específicos desde esta perspectiva.
