# Moriah School demo — runbook

## 1. Open the demo (nothing to install)

Everything runs on the staging servers. Open these in any browser, on a laptop or a phone:

| What | URL |
| --- | --- |
| Moriah website | https://moriah-914241661718.asia-south1.run.app |
| Studio (admin) | https://shikshablox-studio-914241661718.asia-south1.run.app |

**Studio sign-in:** `admin@moriah.test` with the staging demo password (shared separately; stored in Secret Manager as `seed-password`).
Other roles for the approval workflow: `editor@moriah.test` (Editor) and `approver@moriah.test` (Approver), same password.

> Sign-in is rate limited: after several wrong passwords it blocks for a while. Type carefully.
> Staging does not send emails (no mail server is configured); enquiries still appear in Studio → Enquiries.

Open the website and Studio once about 10 minutes before the meeting, to check your internet connection.

<details><summary>Running locally instead (developers)</summary>

Use **Git Bash** (not PowerShell: there `bash` starts WSL). Start Docker Desktop, then:

```bash
docker compose -f compose.yaml -f compose.dev.yaml up -d postgres redis minio minio-init mailpit caddy
bash scripts/dev-backend.sh                                                  # terminal 1
cd apps/site && SITE_REVALIDATE_SECRET=dev-revalidate-secret-0123456789 npx next dev --port 3000   # terminal 2
cd apps/admin && SB_SECURE_COOKIES=false npx next dev --port 3001            # terminal 3
```
Local URLs: http://moriah.shikshablox.localhost, http://admin.shikshablox.localhost, http://mail.shikshablox.localhost.
Dev mode needs about 4 GB free RAM; the "Jest worker" error means the machine ran out of memory.
</details>

## 2. Suggested demo flow (15 minutes)

1. **Website, desktop.** Home: hero photo, key numbers, why families choose Moriah, "Our story" (the founder),
   notice board, gallery, upcoming events, news, admissions banner. Then About → **Founders & Leadership**
   (Mrs. Devakaruna Dasari, Rev. Dasari Bhushana Rao, Bishop Shalem & Mrs. Swarupa, the trustees) →
   **Moriah Home** → Academics → **Our Teachers** (2025–26 staff photos) → Gallery (open "Classes of 2025–26") → News.
2. **Website, phone.** Narrow the browser (or use DevTools device mode): menu button, readable text, tap-friendly buttons.
3. **Admissions enquiry.** Admissions → fill in the form → "Thank you". Then show the enquiry in
   **Studio → Enquiries** (change the status to Contacted, add a note). Live sites email the school office too.
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
- **Leadership details** come from the BFLMI / old school "About" pages (written about 2018): Head Master **Adam Kondru**,
  "around 15 teachers", Bishop **G. Shalem Raju** with Mrs. Swarupa and daughters Hepsi, Beulah and Keziah. Confirm these are current.
- **Teacher names** are taken from the 2025–26 photo file names (Devi, Jyothi, Lakshmi, Premalatha, Rajkumari, Santhi,
  Seshagiri, Mr. Singh, Smily, Sulochana, Venkatesh). Confirm spellings, and add surnames/qualifications if wanted (Studio → Content → Staff).
- **Student numbers:** the BFLMI page says "nearly 200 students" (2018); the site shows "250+" (August 2022 newsletter). Confirm today's figure.
- **Photo rights:** many photos are by Illumination.Photography for BFLMI. Confirm the school may reuse them.
- **Moriah Home children:** individual children's names and stories were deliberately **not** published (child safeguarding).
- Donation and sponsorship content from the old site was **not** carried over (the new site is a school website, not a fundraising site).

## 4. If something goes wrong

- **Pages load slowly:** check the internet connection (the servers respond in well under a second).
- **A published change doesn't show:** refresh once more. Pages also refresh by themselves every 5 minutes.
- **"Too many attempts" at sign-in:** wait 15 minutes, or ask the developer to restart the API service.
- **Redeploy** (developers): `gcloud builds submit --config infra/gcp/cloudbuild.yaml --substitutions=_TAG=vN .`
  then `TAG=vN bash infra/gcp/deploy-staging.sh` (Git Bash).
