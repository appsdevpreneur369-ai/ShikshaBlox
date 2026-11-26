import { releases, type Release } from "@/data/modules";
import { Icon } from "./Icon";

/** Illustrative preview of the upcoming ERP workspace (Lite). Clearly labelled; sample data only. */
export function ErpPreview() {
  const bars = [62, 74, 70, 88, 81, 93, 90];
  return (
    <figure className="overflow-hidden rounded-2xl border border-line bg-card shadow-pop" aria-label="Preview of the ShikshaBlox ERP dashboard with sample data">
      <div className="flex items-center justify-between border-b border-line bg-bg-soft px-4 py-2.5">
        <div className="flex items-center gap-2" aria-hidden="true">
          <span className="size-2.5 rounded-full bg-[#ff5f57]" />
          <span className="size-2.5 rounded-full bg-[#febc2e]" />
          <span className="size-2.5 rounded-full bg-[#28c840]" />
        </div>
        <span className="rounded-full bg-accent-soft px-2.5 py-0.5 text-[0.7rem] font-semibold text-accent-ink">Preview · sample data</span>
      </div>
      <div className="grid grid-cols-[3.25rem_1fr] sm:grid-cols-[10rem_1fr]">
        <div className="border-r border-line bg-bg-soft p-2 sm:p-3" aria-hidden="true">
          {(["home", "users", "check", "wallet", "award", "chat"] as const).map((i, n) => (
            <div
              key={i}
              className={`mb-1 flex items-center gap-2 rounded-lg px-2 py-2 text-xs font-medium ${n === 0 ? "bg-brand text-brand-ink" : "text-ink-3"}`}
            >
              <Icon name={i} className="size-4 shrink-0" />
              <span className="hidden sm:inline">{["Today", "Students", "Attendance", "Fees", "Exams", "Messages"][n]}</span>
            </div>
          ))}
        </div>
        <div className="p-3 sm:p-5">
          <p className="text-xs text-ink-3">Good morning, Principal</p>
          <p className="font-display text-base font-bold text-ink sm:text-lg">Here&apos;s your school today</p>
          <div className="mt-3 grid grid-cols-3 gap-2 sm:gap-3">
            {[
              ["Attendance", "94.6%", "text-live"],
              ["Fees collected", "₹4.82L", "text-brand"],
              ["New enquiries", "12", "text-accent-ink"],
            ].map(([l, v, c]) => (
              <div key={l} className="rounded-xl border border-line p-2 sm:p-3">
                <p className="text-[0.65rem] text-ink-3 sm:text-xs">{l}</p>
                <p className={`font-display text-sm font-extrabold sm:text-xl ${c}`}>{v}</p>
              </div>
            ))}
          </div>
          <div className="mt-3 rounded-xl border border-line p-3">
            <p className="text-xs font-semibold text-ink-2">Attendance this week</p>
            <div className="mt-3 flex h-20 items-end gap-1.5 sm:h-24" aria-hidden="true">
              {bars.map((b, i) => (
                <div key={i} className="flex-1 rounded-t-md bg-gradient-to-t from-[#4338ca] to-[#818cf8]" style={{ height: `${b}%` }} />
              ))}
            </div>
          </div>
          <div className="mt-3 space-y-2">
            {[
              ["bell", "Absence alerts sent to 23 parents on WhatsApp"],
              ["wallet", "Fee reminder scheduled for Class 6–8, 5 pm"],
            ].map(([i, t]) => (
              <div key={t} className="flex items-center gap-2 rounded-lg bg-bg-soft px-3 py-2 text-xs text-ink-2">
                <Icon name={i as "bell"} className="size-4 shrink-0 text-brand" />
                {t}
              </div>
            ))}
          </div>
        </div>
      </div>
    </figure>
  );
}

/** "How ShikshaBlox works": one core platform, modules as blocks, each school isolated. */
export function ArchitectureDiagram() {
  return (
    <figure className="card overflow-hidden p-4 sm:p-8">
      {/* Phones and small tablets: the same story as stacked cards (SVG text would be too small). */}
      <div className="space-y-3 md:hidden">
        <div className="grid grid-cols-3 gap-2 text-center text-xs font-semibold text-ink">
          {["Moriah School", "Your school", "Next campus"].map((s) => (
            <div key={s} className="rounded-xl border border-line px-2 py-3">
              {s}
            </div>
          ))}
        </div>
        <div className="rounded-xl bg-brand-soft px-4 py-3 text-center text-sm font-semibold text-brand">
          Website · Studio · Parent app + WhatsApp · Staff mobile
        </div>
        <div className="rounded-2xl bg-[#3730a3] px-4 py-5 text-center text-white">
          <p className="font-display text-lg font-extrabold">ShikshaBlox core platform</p>
          <p className="mt-1 text-sm text-indigo-100">Identity · Entitlements · Audit · Consent · Files · Notifications · APIs</p>
        </div>
        <div className="flex flex-wrap justify-center gap-2">
          {["Website", "Admissions", "Students", "Attendance", "Fees", "Exams", "Transport", "HR & Payroll"].map((m, i) => (
            <span
              key={m}
              className={`rounded-lg px-3 py-2 text-xs font-bold ${i === 0 ? "bg-[#047857] text-white" : i < 6 ? "bg-[#4f46e5] text-white" : "bg-[#c7d2fe] text-[#1e1b4b]"}`}
            >
              {m}
            </span>
          ))}
        </div>
        <div className="rounded-xl border border-line bg-bg-soft px-4 py-3 text-center text-sm font-medium text-ink-2">
          Row-level security: every school&apos;s data isolated · hosted in India
        </div>
      </div>
      <svg viewBox="0 0 960 520" className="hidden h-auto w-full md:block" role="img" aria-labelledby="arch-title arch-desc">
        <title id="arch-title">How ShikshaBlox works</title>
        <desc id="arch-desc">
          Schools connect through their own website, Studio and parent app to one ShikshaBlox core platform. Modules snap onto the
          core, and each school&apos;s data is isolated.
        </desc>
        <defs>
          <linearGradient id="core" x1="0" x2="1">
            <stop offset="0" stopColor="#3127a8" />
            <stop offset="1" stopColor="#4f46e5" />
          </linearGradient>
        </defs>
        {/* Schools */}
        {[
          ["Moriah School", "moriah.shikshablox.com", 80],
          ["Your school", "yourschool.in", 380],
          ["Next campus", "campus2.yourschool.in", 680],
        ].map(([n, d, x]) => (
          <g key={n as string} transform={`translate(${x},20)`}>
            <rect width="200" height="92" rx="18" fill="var(--c-card)" stroke="var(--c-line)" strokeWidth="2" />
            <text x="100" y="38" textAnchor="middle" fontSize="19" fontWeight="700" fill="var(--c-ink)">
              {n}
            </text>
            <text x="100" y="64" textAnchor="middle" fontSize="13.5" fill="var(--c-ink-3)">
              {d}
            </text>
            <line x1="100" y1="92" x2="100" y2="150" stroke="var(--c-brand)" strokeWidth="2.5" strokeDasharray="6 6" />
          </g>
        ))}
        {/* Channels */}
        <g transform="translate(80,150)">
          <rect width="800" height="64" rx="16" fill="var(--c-brand-soft)" />
          {["School website", "ShikshaBlox Studio", "Parent app + WhatsApp", "Staff mobile"].map((t, i) => (
            <text key={t} x={100 + i * 200} y="39" textAnchor="middle" fontSize="16" fontWeight="600" fill="var(--c-brand)">
              {t}
            </text>
          ))}
        </g>
        {/* Core */}
        <g transform="translate(80,244)">
          <rect width="800" height="96" rx="22" fill="url(#core)" />
          <text x="400" y="42" textAnchor="middle" fontSize="23" fontWeight="800" fill="#fff">
            ShikshaBlox core platform
          </text>
          <text x="400" y="72" textAnchor="middle" fontSize="15" fill="#e0e7ff">
            Identity &amp; roles · Entitlements · Audit · Consent · Files · Notifications · APIs
          </text>
        </g>
        {/* Modules as blocks */}
        {[
          ["Website", "#047857"],
          ["Admissions", "#4f46e5"],
          ["Students", "#4f46e5"],
          ["Attendance", "#4f46e5"],
          ["Fees", "#4f46e5"],
          ["Exams", "#4f46e5"],
          ["Transport", "#c7d2fe"],
          ["HR & Payroll", "#c7d2fe"],
        ].map(([t, c], i) => (
          <g key={t} transform={`translate(${80 + i * 101.5},370)`}>
            <rect width="92" height="58" rx="12" fill={c} />
            <circle cx="30" cy="0" r="7" fill={c} />
            <circle cx="62" cy="0" r="7" fill={c} />
            <text x="46" y="35" textAnchor="middle" fontSize="12.5" fontWeight="700" fill={i === 0 || i < 6 ? "#fff" : "#1e1b4b"}>
              {t}
            </text>
          </g>
        ))}
        {/* Data isolation */}
        <g transform="translate(80,452)">
          <rect width="800" height="52" rx="14" fill="var(--c-bg-soft)" stroke="var(--c-line)" strokeWidth="2" />
          <text x="400" y="32" textAnchor="middle" fontSize="15" fontWeight="600" fill="var(--c-ink-2)">
            PostgreSQL with row-level security: every school&apos;s data isolated · hosted in India
          </text>
        </g>
      </svg>
      <figcaption className="mt-4 flex flex-wrap items-center gap-4 text-sm text-ink-3">
        <span className="inline-flex items-center gap-2">
          <span className="size-3 rounded bg-[#047857]" aria-hidden="true" /> Live now
        </span>
        <span className="inline-flex items-center gap-2">
          <span className="size-3 rounded bg-[#4f46e5]" aria-hidden="true" /> Lite / Standard (2027)
        </span>
        <span className="inline-flex items-center gap-2">
          <span className="size-3 rounded bg-[#c7d2fe]" aria-hidden="true" /> Standard / Global
        </span>
      </figcaption>
    </figure>
  );
}

export const roadmapDetail: Record<Release, string[]> = {
  R1: ["Website & CMS, 14 standard pages", "Notices, news, events, gallery", "Enquiry inbox, disclosures", "Approvals, scheduling, rollback"],
  R2: ["Setup wizard & data import", "Students, guardians, certificates", "Attendance (offline) & parent alerts", "Fees Lite, report cards, WhatsApp, parent app"],
  R3: ["Admissions CRM", "Fees Standard, timetable, HR & payroll", "Transport + GPS, library, gate", "Dashboards & helpdesk"],
  R4: ["Curriculum packs (CBSE HPC, ICSE, State)", "Exams Standard, lesson plans", "Homework submissions", "Behaviour & safeguarding"],
  R5: ["IB / Cambridge / AP assessment", "Transcripts & counselling", "Multi-currency billing", "LMS, SSO, ECA, trips"],
  R6: ["Multi-campus control", "APIs & webhooks", "AI co-pilots", "Hostel, facilities, IT assets"],
};

export function RoadmapTimeline({ compact = false }: { compact?: boolean }) {
  const list = Object.entries(releases) as [Release, (typeof releases)[Release]][];
  return (
    <ol className={`relative grid gap-4 ${compact ? "sm:grid-cols-2 lg:grid-cols-3" : "md:grid-cols-2 lg:grid-cols-3"}`}>
      {list.map(([id, r]) => (
        <li key={id} className={`card card-hover reveal relative p-6 ${r.live ? "ring-2 ring-[color-mix(in_oklab,var(--c-live)_45%,transparent)]" : ""}`}>
          <div className="flex items-center justify-between gap-3">
            <span className="font-display text-sm font-bold text-ink-3">{id}</span>
            <span
              className={`rounded-full px-2.5 py-1 text-xs font-semibold ${r.live ? "bg-live-soft text-live" : "bg-accent-soft text-accent-ink"}`}
            >
              {r.target}
            </span>
          </div>
          <h3 className="mt-3 font-display text-lg font-bold text-ink">{r.name}</h3>
          {!compact && (
            <ul className="mt-3 space-y-1.5 text-sm text-ink-2">
              {roadmapDetail[id].map((d) => (
                <li key={d} className="flex gap-2">
                  <Icon name="check" className="mt-0.5 size-4 shrink-0 text-brand" />
                  {d}
                </li>
              ))}
            </ul>
          )}
        </li>
      ))}
    </ol>
  );
}
