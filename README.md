# ShikshaBlox — Composable School Operating Platform

Modular, multi-tenant School ERP. This repository starts with **M01 — School Website & CMS**.
The product spec is in [`docs/ShikshaBlox-Schools-ERP-v2.md`](docs/ShikshaBlox-Schools-ERP-v2.md); architecture decisions are in [`docs/adr`](docs/adr/README.md).

## Repository layout

```
backend/            Java 21 + Spring Boot 3.5 modular monolith (Spring Modulith)
  com.shikshablox.platform.*   tenancy, identity, entitlement, config, audit, files, consent, notification, shared
  com.shikshablox.modules.*    website (M01), disclosure, admissions (stub), recruitment (stub)
apps/site           Public school websites (Next.js 16, multi-tenant by host name)
apps/admin          ShikshaBlox Studio — admin portal / CMS
apps/www            shikshablox.com — the product website (static Next.js; see docs/adr/0008)
packages/ui         Design system components + design tokens
packages/blocks     Page block schemas (zod → JSON Schema, shared with the backend)
packages/api-client Typed API client generated from docs/api/openapi.json
packages/config     Shared tsconfig / ESLint
infra/              Caddy, Postgres init, MinIO init, Next.js Dockerfile
e2e/                Playwright end-to-end tests
docs/               Spec, ADRs, OpenAPI document
```

## Prerequisites

- Docker Desktop (8 GB RAM or more assigned)
- For local development without Docker builds: Java 21, Node 22, pnpm 10 (`npm i -g pnpm@10`)

## Run everything (one command)

```powershell
copy .env.example .env      # optional; sensible dev defaults are built in
docker compose up --build
```

The first build downloads dependencies and takes a while. On machines with limited memory, build the images one at a time first: `docker compose build backend; docker compose build site; docker compose build admin`.

| URL                                              | What                                                        |
| ------------------------------------------------ | ----------------------------------------------------------- |
| http://demo.shikshablox.localhost                | A school website (any `<name>.shikshablox.localhost` works) |
| http://admin.shikshablox.localhost               | ShikshaBlox Studio (admin)                                  |
| http://api.shikshablox.localhost/swagger-ui.html | API docs                                                    |
| http://api.shikshablox.localhost/actuator/health | Backend health                                              |
| http://mail.shikshablox.localhost                | Mailpit (captured emails)                                   |
| http://localhost:9001                            | MinIO console                                               |
| `localhost:5433`                                 | PostgreSQL (`sb_admin` superuser; app uses `sb_app`)        |

`*.localhost` resolves to your machine in Chrome, Edge and Firefox with no hosts-file changes. If port 80 is busy, set `SB_HTTP_PORT=8088` in `.env` and add `:8088` to the URLs.

**Custom domains locally:** add a line such as `127.0.0.1 www.example-school.test` to `C:\Windows\System32\drivers\etc\hosts` (admin PowerShell).

## Demo sign-ins (local seed data)

All accounts are fictional (`.test` addresses) and use the password from `SEED_PASSWORD` (default `Shiksha@Dev2026`).

| Account                                                                  | Role                                                                     |
| ------------------------------------------------------------------------ | ------------------------------------------------------------------------ |
| `superadmin@shikshablox.test`                                            | Platform super admin (must set up an authenticator app on first sign-in) |
| `admin@<school>.test`                                                    | School admin                                                             |
| `editor@<school>.test`, `approver@<school>.test`, `viewer@<school>.test` | Content roles                                                            |

Schools: `moriah`, `greenfield`, `sunrise`, `horizon`, plus the internal `sb-sales` tenant whose enquiry inbox receives shikshablox.com demo requests — e.g. http://moriah.shikshablox.localhost. The "Email code" sign-in sends a code to Mailpit (http://mail.shikshablox.localhost).

## Develop with hot reload

```powershell
docker compose -f compose.yaml -f compose.dev.yaml up -d postgres redis minio minio-init mailpit caddy
pnpm install
pnpm dev                                   # site :3000, admin :3001, www :3002
cd backend; .\mvnw spring-boot:run         # api :8080
```

Caddy still gives you the `*.shikshablox.localhost` URLs, pointing at the apps on your host.

## Quality checks

```powershell
cd backend; .\mvnw verify                  # format check, unit, modularity and Testcontainers integration tests
pnpm lint; pnpm typecheck; pnpm test; pnpm build
pnpm api:generate                          # regenerate the TS client after API changes
pnpm format                                # Prettier
cd backend; .\mvnw spotless:apply          # google-java-format
```

End-to-end (against the running stack): `pnpm --filter @shikshablox/e2e install-browsers` once, then `pnpm e2e`.

Product website (`apps/www`, no backend needed for browsing): `pnpm --filter @shikshablox/www build; pnpm --filter @shikshablox/www start`, then
`cd e2e; $env:WWW_URL="http://localhost:3002"; pnpm exec playwright test -c playwright.www.config.ts` (7 device profiles, axe accessibility,
links, images, navigation, form). Prices on the site come from `docs/PRICING-MATH.md`; no release dates are published (ADR 0008).

The backend integration tests need Docker running (Testcontainers starts PostgreSQL 16).

## Configuration and secrets

All settings come from environment variables; see [`.env.example`](.env.example). `.env` is git-ignored. The built-in defaults are for local development only.

## Copyright

© aurora-blossom.com. All rights reserved.
