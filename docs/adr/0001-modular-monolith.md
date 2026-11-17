# ADR 0001: Modular monolith with Spring Modulith

- Status: Accepted
- Date: 2026-11-17

## Context
The spec (§16.1) calls for a modular monolith first, with services extracted later only when scale, ownership or isolation justify it. Every module must remain independently enableable through entitlements.

## Decision
- One Spring Boot 3.5 / Java 21 application, `com.shikshablox`.
- Application modules are declared explicitly with `@ApplicationModule` in `package-info.java` (`spring.modulith.detection-strategy=explicitly-annotated`):
  - platform: `tenancy`, `identity`, `entitlement`, `config`, `audit`, `files`, `consent`, `notification`, plus an OPEN `shared` kernel (request id, error model).
  - product: `website` (M01), `disclosure` (M02 seam), `admissions` (M03 seam), `recruitment` (M39 seam).
- Modules talk through their public API package (top-level package of the module) or domain events. Internals live in sub-packages and are not accessible to other modules. `ModularityTests` fails the build on violations.
- Cross-module workflows use Spring Modulith's event publication registry (JDBC transactional outbox, table `event_publication`). No broker until justified.
- One PostgreSQL database; each module owns its tables (table prefix per module) and never reads another module's tables.

## Consequences
- Simple deployment and local development; refactoring across modules stays cheap.
- Extracting a module later (e.g. website rendering, notifications) means swapping in-process events for a broker; contracts already exist.
