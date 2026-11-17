# ADR 0005: Authentication and session model

- Status: Accepted (implemented in M1a)
- Date: 2026-11-17

## Decision
- Spring Security, stateless API. Short-lived **JWT access tokens** (15 min, HS256 locally / asymmetric keys in production) carrying user id, active organization and roles.
- **Rotating refresh tokens** (opaque, hashed in DB, 30 days, one-time use with reuse detection that revokes the whole token family).
- The admin app keeps tokens in **httpOnly, Secure, SameSite=Strict cookies** set by its own Next.js server (backend-for-frontend); browser JavaScript never sees them.
- Login: email + password (Argon2id), or **email OTP** (6 digits, 10-minute expiry, attempt limits) behind an `OtpSender` interface (SMS/WhatsApp later).
- **TOTP MFA** (RFC 6238) is mandatory for `PLATFORM_SUPER_ADMIN` and can be enforced per organization for `SCHOOL_ADMIN`.
- Brute-force protection: per-account and per-IP rate limits (Redis), generic error messages, audit of every login, failure and MFA event.

## Implementation notes (M1a)
- Access JWT claims: `sub`, `org` (active school), `mfa`, `amr`, `typ=access`. MFA challenge tokens (`typ=mfa_challenge`, 5 min) are rejected by the API.
- Permissions are **re-read from the database on every request** (role assignments + user status), so revoking a role or disabling a user takes effect immediately. Platform permissions require `mfa=true`.
- TOTP: RFC 6238 SHA-1/6 digits/30 s, ±1 step, replay of an accepted step is rejected; seeds are AES-256-GCM encrypted at rest.
- Lockout after 5 failed passwords for 15 minutes; failures return one generic message; unknown e-mails do equal-cost hashing.
- Rate limits are in-memory per instance for now (single instance in staging). TODO: Redis-backed limiter when scaling out.
- The admin app (Next.js) is the BFF: server actions call the API, tokens live in httpOnly `SameSite=Strict` cookies, and `proxy.ts` rotates the refresh token when the access cookie expires.
- Security events (`SecurityEvent`) are published for the audit module (M1b).
