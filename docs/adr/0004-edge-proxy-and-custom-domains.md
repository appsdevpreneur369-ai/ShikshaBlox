# ADR 0004: Edge proxy, local hostnames and custom-domain TLS

- Status: Accepted (custom-domain flow implemented in M7)
- Date: 2026-11-17

## Context
Schools get `<school>.shikshablox.app` and (Web Pro) a custom domain with automatic SSL.

## Decision
- **Local:** Caddy on port 80 routes `admin.`, `api.`, `media.`, `mail.` subdomains of `shikshablox.localhost`; every other host goes to the site app. Browsers resolve `*.localhost` to 127.0.0.1 without hosts-file edits. Custom domains are simulated with hosts-file entries.
- **Production (recommended):** Caddy with **on-demand TLS**. Caddy calls an `ask` endpoint on the backend (`/internal/domains/allowed?domain=`) that returns 200 only for domains that passed DNS TXT verification and are active, so certificates are never issued for arbitrary hosts. Certificates are stored in shared storage for multiple Caddy instances.
- **Alternative:** Cloudflare for SaaS (custom hostnames) if a CDN/WAF in front is required at scale; the backend verification flow stays the same.
- Verification flow: school adds domain → we show CNAME (or A record for apex) + TXT token → background job checks DNS → status `pending → verified → active` (or `failed`), re-checked periodically.
