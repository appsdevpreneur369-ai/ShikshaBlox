# ADR 0005: Authentication and session model

- Status: Accepted (implementation in M1a)
- Date: 2026-11-17

## Decision
- Spring Security, stateless API. Short-lived **JWT access tokens** (15 min, HS256 locally / asymmetric keys in production) carrying user id, active organization and roles.
- **Rotating refresh tokens** (opaque, hashed in DB, 30 days, one-time use with reuse detection that revokes the whole token family).
- The admin app keeps tokens in **httpOnly, Secure, SameSite=Strict cookies** set by its own Next.js server (backend-for-frontend); browser JavaScript never sees them.
- Login: email + password (Argon2id), or **email OTP** (6 digits, 10-minute expiry, attempt limits) behind an `OtpSender` interface (SMS/WhatsApp later).
- **TOTP MFA** (RFC 6238) is mandatory for `PLATFORM_SUPER_ADMIN` and can be enforced per organization for `SCHOOL_ADMIN`.
- Brute-force protection: per-account and per-IP rate limits (Redis), generic error messages, audit of every login, failure and MFA event.
