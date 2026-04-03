# CHANGELOG_SOURCES.md

## Feature: Worker Health Check — foodtech_worker

Bitácora de investigación. Compara fuentes IA vs documentación oficial. Sin código — diseño puro.

---

## 1. Diseño de Feature

### a) Humano

#### Qué es Arquitectura Hexagonal
1. https://alistair.cockburn.us/hexagonal-architecture/

#### Qué es Health Check API Pattern
1. https://microservices.io/patterns/observability/health-check-api.html

#### Diseño de software
1. https://betweendata.io/posts/ports-and-adapters-spring/

### b) IA
- Proporcionó una estructura base para organizar el contenido del documento.

---

## 2. Planteamiento del Problema

### a) Humano

#### Qué es el Outbox Pattern
1. https://microservices.io/patterns/data/outbox.html

#### Qué es un Scheduler
1. https://docs.spring.io/spring-boot/reference/integration/scheduling.html

#### Qué es RabbitMQ
1. https://www.rabbitmq.com/tutorials/tutorial-one-java.html

### b) IA
- Generación de preguntas para profundizar en el problema: "¿Qué pasa si el scheduler se detiene?", "¿Cuántos eventos fallidos son aceptables?"

---

## 3. Historias de Usuario

### a) Humano

#### Qué es
1. https://www.atlassian.com/es/agile/project-management/user-stories

#### Criterios de aceptación
1. https://www.atlassian.com/es/work-management/project-management/acceptance-criteria
2. https://resources.scrumalliance.org/Article/need-know-acceptance-criteria

#### Definition of Done
1. https://www.atlassian.com/es/agile/project-management/definition-of-done
2. https://www.scrumio.com/scrum/definicion-de-hecho

### b) IA
- Proporcionó HU enfocadas en verificar si el servicio está operativo.

---

## 4. Spring Boot Actuator

### a) Humano

#### Documentación oficial
1. https://docs.spring.io/spring-boot/reference/actuator/endpoints.html
2. https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.health.writing-custom-health-indicators

#### Fuentes complementarias
1. https://www.baeldung.com/spring-boot-health-indicators
2. https://www.amitph.com/custom-health-check-spring-boot-actuator/
3. https://fabianlee.org/2022/06/29/java-adding-custom-health-indicator-to-spring-boot-actuator/

### b) IA
1. *(Fuera del alcance)* https://learn.microsoft.com/en-us/azure/role-based-access-control/overview
2. *(Alucinación)* https://www.atlassian.com/work-management/product-management/product-requirements

---

## 5. Custom HealthIndicator

### a) Humano

#### Cómo crear un HealthIndicator propio
1. https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.health.writing-custom-health-indicators
2. https://www.baeldung.com/spring-boot-health-indicators
3. https://docs.spring.io/spring-boot/api/java/org/springframework/boot/actuate/health/AbstractHealthIndicator.html

### b) IA
- No propuso un indicador personalizado, se enfocó en la solución genérica del Actuator.

---

## 6. Arquitectura Hexagonal

### a) Humano

#### Qué es Arquitectura Hexagonal
1. https://alistair.cockburn.us/hexagonal-architecture/
2. https://betweendata.io/posts/ports-and-adapters-spring/

#### Ports & Adapters
1. https://refactoring.guru/design-patterns

### b) IA
- Propuso acceder directamente a JPA y RabbitTemplate desde un Controller.

---

## 7. Contrato de Respuesta

### a) Humano

#### Estándar IETF para health check
1. https://tools.ietf.org/id/draft-inadarei-api-health-check-06.html
2. https://datatracker.ietf.org/doc/rfc9457/

### b) IA
- Propuso una respuesta mínima con solo status global.

---

## 8. Seguridad del Endpoint

### a) Humano

#### OWASP API3:2023
1. https://owasp.org/API-Security/editions/2023/en/0xa3-broken-object-property-level-authorization/

#### Configuración de seguridad en Spring
1. https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.endpoints.security

### b) IA
- No propuso medidas de seguridad para el endpoint.

---

## 9. Diseño de Software

### a) Humano

#### Diseño de software
1. https://alistair.cockburn.us/hexagonal-architecture/
2. https://betweendata.io/posts/ports-and-adapters-spring/

### b) IA
- Propuso un diseño basado en Controllers que acceden directamente a infraestructura.

---

## 10. Modelado de Estados

### a) Humano

#### Diagrama de estados
1. https://www.lucidchart.com/pages/es/diagrama-de-maquina-de-estados
2. https://miro.com/es/diagrama/que-es-diagrama-maquina-estados-uml/

### b) IA
- No propuso un modelo de estados específico para los componentes.

---

## 11. Decisiones Técnicas (ADR)

### a) Humano

#### ADR - Architecture Decision Records
1. https://alistair.cockburn.us/hexagonal-architecture/
2. https://microservices.io/patterns/observability/health-check-api.html

### b) IA
- Las propuestas de la IA fueron reevaluadas y ajustadas con la investigación humana.

---

## 12. Análisis de Riesgos

### a) Humano

#### Análisis de riesgos
1. https://owasp.org/www-project-top-ten/
2. https://microservices.io/patterns/observability/health-check-api.html

### b) IA
- No se identificaron riesgos específicos desde esta perspectiva.
