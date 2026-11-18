# Moriah School demo — runbook

## 1. Start everything (about 2 minutes)

Open **Docker Desktop** first and wait until it says it is running. Then, in Git Bash at the repo root:

```bash
# Infrastructure: Postgres, Redis, MinIO (media), Mailpit (email), Caddy (local domains)
docker compose -f compose.yaml -f compose.dev.yaml up -d postgres redis minio minio-init mailpit caddy

# API (terminal 1) — seeds Moriah on first run
bash scripts/dev-backend.sh

# Public website (terminal 2)
cd apps/site && SITE_REVALIDATE_SECRET=dev-revalidate-secret-0123456789 npx next dev --port 3000

# Studio (terminal 3)
cd apps/admin && SB_SECURE_COOKIES=false npx next dev --port 3001
```

Ready when terminal 1 prints `Started ShikshaBloxApplication` and the other two print `Ready`.
Open each URL below once before the meeting so the first page load is fast (dev mode compiles on first visit).

| What | URL |
| --- | --- |
| Moriah website | http://moriah.shikshablox.localhost |
| Studio (admin) | http://admin.shikshablox.localhost |
| Emails sent by the system | http://mail.shikshablox.localhost |

**Studio sign-in:** `admin@moriah.test` with the seed password (`SEED_PASSWORD`, default in `scripts/dev-backend.sh`).
Other roles to show the approval workflow: `editor@moriah.test` (Editor) and `approver@moriah.test` (Approver), same password.

> Sign-in is rate limited. If you see "Too many attempts", restart terminal 1 (API) to clear it.

## 2. Suggested demo flow (15 minutes)

1. **Website, desktop.** Home: hero photo, key numbers, why families choose Moriah, notice board, gallery,
   upcoming events, news, admissions banner. Then About → Academics → Facilities → Gallery (open an album) → News (open a story).
2. **Website, phone.** Narrow the browser (or use DevTools device mode): menu button, readable text, tap-friendly buttons.
3. **Admissions enquiry.** Admissions → fill in the form → "Thank you". Then show the email alert in Mailpit
   and the enquiry in **Studio → Enquiries** (change the status to Contacted, add a note).
4. **Edit a page live.** Studio → Pages → Home → open "Hero banner" → change the heading → **Preview**
   (yellow "preview" bar, visitors can't see it) → **Publish** → refresh the website: changed within seconds.
   Then **History → Restore** to bring the previous version back.
5. **Add a notice.** Studio → Content → Notices → Add → "Publish" → it appears on the home page notice board.
6. **Design.** Studio → Design & profile → try another colour preset or theme → Save → refresh the website.
   (Switch back to *Teal & rose* / *Modern* afterwards.)
7. **Approval workflow (optional).** Sign in as `editor@moriah.test`: the editor sees **Submit for approval** instead of Publish.
   Sign in as `approver@moriah.test` → the page shows **Approve & publish / Request changes**.
8. **Trust and safety.** Media library: photos of identifiable students only appear on the website when a consent record covers them.
   Audit log: every change is recorded.

## 3. Content for the school to confirm

The launch content was rebuilt from the previous website (moriaheducation.com) into the standard 14-page structure.
Please ask the school to confirm or correct the following:

- **Photo consent.** Student photos were carried over from the school's previous public website under a school declaration
  recorded as *"migrated — school to confirm"* (Studio → Settings → Consent). The school should confirm it or ask us to remove photos.
- **Principal's Message.** Placeholder text signed "The Principal": needs the principal's real message (and photo, if wanted).
- **Contact details.** Phone number and school email are not on the site yet (only the address, which is from the old site).
- **Starter notices and events.** "Admissions open for 2027–28", the parent–teacher meeting, Annual Day rehearsals, the science exhibition,
  Annual Day and the Sankranti holiday are **sample items with sample dates**. Replace them with real ones or delete them.
- **Facts and figures.** "250+ students" (from the August 2022 newsletter) and "8 early childhood classrooms" — confirm current numbers.
- **Admissions.** The documents list and the 2027–28 admissions year are typical examples. Confirm them.
- **Board / recognition line.** "State Board · Recognised by the Government of Andhra Pradesh · English medium" (from the school banner).
- **Staff directory and careers** are empty on purpose. The school adds staff (with consent) and vacancies in Studio.
- Donation and sponsorship content from the old site was **not** carried over (the new site is a school website, not a fundraising site).

## 4. If something goes wrong

- **Website shows "We couldn't find that page" for everything:** the API isn't running. Check terminal 1.
- **Images missing:** Docker / MinIO is stopped. Re-run the `docker compose ... up -d` line.
- **A published change doesn't show:** refresh once more. Pages also refresh by themselves every 5 minutes.
- **Start completely fresh** (wipes local data, then Moriah is re-seeded on the next API start):
  `docker compose down && docker volume rm shikshablox_pgdata shikshablox_miniodata`.
