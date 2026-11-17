# ADR 0007: Entitlements, configuration hierarchy and audit log

- Status: Accepted (M1b)
- Date: 2026-11-17

## Entitlements
- `module_catalogue` and `plan` are reference data (read-only for the runtime role). A plan is a JSON bundle of `{module, depth, features, limits}`; applying a plan writes per-school `entitlement` rows. Only the system context can write entitlements (RLS), so schools cannot grant themselves anything.
- Seeded plans (spec §22.3, plus the decision that the 14 standard pages are always included):

| Limit | Web Free | Web Pro |
|---|---|---|
| custom_pages (beyond the 14 standard pages) | 5 | unlimited (-1) |
| storage_bytes | 2 GB | 10 GB |
| languages | 1 | 2 |
| custom_domains | 0 | 1 |
| features | content types, analytics | + approval workflow, custom domain, remove badge, export/import |

- `EntitlementService.checkCanAdd` blocks at 100%; `status` reports WARNING from 80%. Usage comes from `UsageMeter` beans owned by the module that holds the data.
- New schools get Web Free via the `OrganizationCreated` event.

## Configuration
- Keys are registered in `ConfigKey` (type, default, school-editable). Resolution: DEFAULT → PLATFORM → PROFILE (organization.config_profile) → ORGANIZATION → CAMPUS; the most specific wins and the UI shows the source.
- Example: `security.require_mfa_school_admin` makes sign-in demand authenticator enrolment for that school's administrators.

## Audit
- `audit_log` is append-only: UPDATE/DELETE/TRUNCATE revoked from the runtime role and blocked by a trigger. RLS scopes reads to the school.
- Higher modules call `AuditService` inside their transaction; lower modules (tenancy, identity, config) publish events that audit records, which keeps the module graph acyclic.
