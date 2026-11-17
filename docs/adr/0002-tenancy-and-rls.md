# ADR 0002: Tenancy model, tenant resolution and Row-Level Security

- Status: Accepted (implementation in M1a)
- Date: 2026-11-17

## Context
§16.5: every tenant-owned record carries the tenant boundary, enforced in the service layer and the database; never trust a tenant id from the browser; automated cross-tenant tests.

## Decision
- Hierarchy `Organization → Campus` (single default campus). Every tenant-owned table has `organization_id UUID NOT NULL` (and `campus_id` where relevant).
- **Tenant resolution (server-side only):**
  - Public site: Host header → `site_domain` lookup (platform subdomain or verified custom domain) → organization.
  - Admin/API: organization from the authenticated principal's membership (JWT claim, re-validated against the DB). Switching school is a server call that issues a new token.
- **Database roles:** `sb_owner` owns the schema and runs Flyway; the runtime role `sb_app` has DML only, is not an owner and has `NOBYPASSRLS`, so RLS always applies to it.
- **RLS:** every tenant table has `ENABLE` + `FORCE ROW LEVEL SECURITY` and a policy `organization_id = current_setting('app.org_id')::uuid`. The application sets `SET LOCAL app.org_id` at the start of each transaction from the resolved tenant context. No tenant context ⇒ no rows.
- Platform-wide jobs (scheduler, super-admin tools) use an explicit, audited "system context", never a silent bypass.
- Tests: Testcontainers runs the same two roles; isolation tests attempt cross-tenant reads/writes through both the service layer and raw SQL and must fail.

## Consequences
- A missed `WHERE organization_id = ?` in application code cannot leak data.
- Every query must run inside a transaction with tenant context; this is enforced by a connection/transaction hook.
