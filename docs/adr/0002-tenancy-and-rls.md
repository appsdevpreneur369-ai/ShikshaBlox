# ADR 0002: Tenancy model, tenant resolution and Row-Level Security

- Status: Accepted (implemented in M1a)
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

## Implementation notes (M1a)
- `TenantContext` (thread-bound) holds organization, user and an explicit `system` flag. The application DataSource is wrapped so that **every connection checkout** runs `set_config('app.org_id' | 'app.user_id' | 'app.system', …)`; values are always overwritten (blank when absent), so a pooled connection cannot carry an earlier request's tenant. Opening a context inside a running transaction throws.
- Policies use `sb_current_org()`, `sb_current_user()` and `sb_is_system()`. System context is only entered through `TenantContext.system(reason)` (login, onboarding, membership listing) and is logged.
- Anonymous host lookups go through two `SECURITY DEFINER` functions (`sb_resolve_slug`, `sb_resolve_domain`) that return only an organization id. Because `FORCE ROW LEVEL SECURITY` also binds the owner, the owner role (migrations only, never application traffic) has read-only policies on `organization` and `site_domain`.
- Flyway uses its own owner connection; the application pool connects as `sb_app`.
- Tests: `RlsIsolationIT` (raw SQL as `sb_app`: cross-tenant reads return nothing, cross-tenant inserts/moves fail, updates/deletes affect 0 rows, no context = no rows, the role cannot disable RLS, pooled connections don't leak context) and `AuthFlowIT`.
