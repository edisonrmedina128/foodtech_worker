<!--
## Sync Impact Report

**Version change**: (none) → 1.0.0
**Modified principles**: N/A — initial ratification
**Added sections**:
  - Core Principles (I–IV)
  - Technology Stack & Adapter Boundaries
  - Development Workflow & Quality Gates
  - Governance
**Removed sections**: N/A
**Templates requiring updates**:
  - ✅ `.specify/memory/constitution.md` — this file
  - ✅ `.specify/templates/plan-template.md` — Constitution Check gates align with principles I–IV
  - ✅ `.specify/templates/spec-template.md` — no structural changes required; existing format compatible
  - ✅ `.specify/templates/tasks-template.md` — task phases align with hexagonal layers and test discipline
**Deferred TODOs**: None
-->

# foodtech_worker Constitution

## Core Principles

### I. Hexagonal Architecture (NON-NEGOTIABLE)

The domain layer MUST NEVER depend on infrastructure. Dependency arrows always
point inward: adapters depend on ports, ports depend on nothing external.

- The **application layer** owns Use Cases; all business logic lives there.
- **Driver Adapters** (input side): REST controllers, schedulers, health indicators.
- **Driven Adapters** (output side): JPA repositories, RabbitMQ publishers.
- Ports are defined as Java interfaces inside the domain/application layer.
- No Spring annotations, JPA imports, or messaging imports are permitted inside
  `domain/` or `application/` packages.
- Zero circular dependencies between modules or packages — enforced at build time
  (ArchUnit or equivalent).

**Rationale**: Isolation ensures the core billing logic of the FoodTech ecosystem
can be tested, replaced, and reasoned about independently of any framework or
infrastructure choice.

### II. SOLID & Clean Code (NON-NEGOTIABLE)

Every class MUST have a single, clearly named responsibility (SRP). Code that
cannot be explained in one sentence MUST be refactored.

- Open/Closed: extend via new Use Cases or adapters; do not modify existing ones
  to add unrelated behaviour.
- Liskov/Interface Segregation: prefer narrow, purpose-specific port interfaces
  over wide, catch-all contracts.
- Dependency Inversion: constructors receive interfaces, never concrete
  implementations (constructor injection only).
- AI-generated code MUST be reviewed, understood, and owned by a human developer
  before merging. "Copy-paste from AI without review" is prohibited.
- Spaghetti code (tangled control flow, mixed concerns, unnamed magic values) is
  a blocking defect.

**Rationale**: Maintainability and legibility are first-class requirements in a
long-lived billing microservice. Intelligence of the code is a distant secondary
concern.

### III. Test Discipline (NON-NEGOTIABLE)

Unit tests MUST be written with full isolation using Mockito mocks/stubs. No
Spring context is permitted to start during unit test execution.

- Unit tests MUST cover both the happy path AND edge cases (errors, threshold
  values, boundary states).
- Tests serve as living documentation of observable behaviour; test names MUST
  describe intent in plain language.
- A class without a corresponding unit test for its public contract MUST NOT be
  merged to the main branch.
- Integration tests (Testcontainers or similar) are limited to adapter-level
  concerns: actual DB persistence, actual RabbitMQ publish/consume.
- The test pyramid is enforced: unit >> integration >> e2e.

**Rationale**: Fast, isolated tests enable confident refactoring and onboarding.
Tests that require a running container slow down every developer iteration and
hide design coupling.

### IV. Development Philosophy

The human developer is the architect; the AI assistant is a writing aid.

- Every architectural decision MUST be explainable and defensible verbally by the
  developer who introduced it.
- Maintainability and readability MUST be prioritised over cleverness or brevity.
- The **Outbox Pattern** is the mandatory mechanism for reliable event publication
  to RabbitMQ; direct publishing without outbox is prohibited in production paths.
- All configuration MUST be externalised via `application.properties` /
  `application-{profile}.properties`. Hardcoded environment-specific values are a
  blocking defect.
- YAGNI: do not build abstractions or features that have no current requirement.

**Rationale**: A microservice in a multi-restaurant billing ecosystem will be
maintained by multiple developers over time. Clarity and defensible decisions
reduce onboarding friction and operational risk.

## Technology Stack & Adapter Boundaries

**Runtime**: Java 17 · Spring Boot 3.5
**Build**: Gradle
**Persistence**: Spring Data JPA (Driven Adapter — `infrastructure/persistence/`)
**Messaging**: RabbitMQ via Spring AMQP (Driven Adapter — `infrastructure/messaging/`)
**Input adapters**: Spring MVC REST controllers, `@Scheduled` schedulers,
  Spring Boot Actuator health indicators (`infrastructure/rest/`,
  `infrastructure/scheduler/`, `infrastructure/health/`)
**Test**: JUnit 5 · Mockito · Testcontainers (integration only)
**Architecture enforcement**: ArchUnit rules executed on every build

Package contract:

```
com.foodtech.foodtech_worker
├── domain/          ← entities, value objects, domain exceptions (NO framework deps)
├── application/     ← use cases, port interfaces, application services
└── infrastructure/  ← all adapters, Spring config, JPA entities/repos, AMQP config
```

## Development Workflow & Quality Gates

**Before opening a PR**:
1. All unit tests MUST pass (`./gradlew test`).
2. ArchUnit architecture tests MUST pass (no layer violations).
3. No new classes in `domain/` or `application/` importing infrastructure packages.
4. All configuration values externalised — no hardcoded strings for URLs, queues,
   credentials, or thresholds.
5. Outbox mechanism used for any new event publication path.

**Code review checklist**:
- Does the change respect hexagonal layer boundaries? (Principle I)
- Does each new class have a single clear responsibility? (Principle II)
- Is AI-generated code reviewed and understood? (Principle II)
- Are unit tests present and free of Spring context? (Principle III)
- Are edge cases covered in tests? (Principle III)
- Can the developer explain every decision verbally? (Principle IV)

## Governance

This constitution supersedes all other development practices and informal
agreements within the `foodtech_worker` service.

- Amendments require: written proposal, clear rationale, version bump per the
  semantic versioning policy below, and approval by the technical lead.
- **Versioning policy**:
  - MAJOR — backward-incompatible removal or redefinition of a principle.
  - MINOR — new principle or section added; materially expanded guidance.
  - PATCH — clarifications, wording improvements, non-semantic refinements.
- All pull requests MUST include a Constitution Check section in the plan or PR
  description confirming compliance with Principles I–IV.
- Complexity violations (intentional deviations) MUST be documented in the plan's
  Complexity Tracking table with explicit justification.
- This constitution is reviewed at the start of each new major feature cycle.

**Version**: 1.0.0 | **Ratified**: 2026-04-06 | **Last Amended**: 2026-04-06
