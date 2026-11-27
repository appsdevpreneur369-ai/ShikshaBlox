import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { CheckCircle2, Sparkles } from "lucide-react";
import { api, currentUser } from "@/lib/session";

export const metadata: Metadata = { title: "Plan & usage" };

const LIMIT_LABEL: Record<string, string> = {
  custom_pages: "Custom pages (the 14 standard pages are always included)",
  storage_bytes: "Storage",
  languages: "Website languages",
  custom_domains: "Custom domains",
  contacts: "Message contacts",
  custom_fields: "Custom student fields",
};

const FEATURE_LABEL: Record<string, string> = {
  notices: "Notices & circulars",
  events: "Events calendar",
  gallery: "Photo & video gallery",
  staff: "Staff directory",
  news: "News",
  faqs: "FAQs",
  careers: "Careers",
  analytics: "Analytics",
  approval_workflow: "Approval workflow",
  custom_domain: "Custom domain",
  remove_branding: "Remove ShikshaBlox badge",
  export_import: "Site export & import",
  review_reminders: "Review reminders",
  csv_export: "CSV export",
  email_alerts: "Email alerts",
  pipeline: "Lead pipeline",
  follow_ups: "Follow-up reminders",
  walk_ins: "Walk-in & phone leads",
  counsellors: "Counsellor assignment",
  seat_matrix: "Seats per class",
  funnel_analytics: "Funnel analytics",
  online_application: "Online application form",
  calendar: "Compliance calendar",
  evidence_vault: "Evidence vault",
  reminders: "Reminders",
  owners: "Item owners",
  export_pack: "Inspection export pack",
  register: "Walk-in & call register",
  appointments: "Appointments",
  post: "Post & couriers",
  lost_found: "Lost & found",
  ptm: "PTM slot booking",
  helpdesk: "Parent helpdesk",
  surveys: "Feedback surveys",
  sla: "Helpdesk SLA tracking",
  assignment: "Request assignment",
  broadcasts: "Circulars & alerts",
  templates: "Message templates",
  consent_register: "Opt-in register",
  acknowledgements: "Read receipts & acknowledgements",
  multilingual: "Multilingual templates",
  scheduling: "Scheduling & quiet hours",
  profiles: "Student 360 profiles",
  guardians: "Guardians & pickup rules",
  documents: "Student documents",
  import: "CSV import",
  promotion: "Year-end promotion",
  apaar: "APAAR tracking",
  custom_fields: "Custom fields",
  children: "Children's details",
  circulars: "Circulars & acknowledgements",
  requests: "Helpdesk requests",
  preferences: "Message preferences",
  daily: "Daily attendance on a phone",
  alerts: "Absence alerts to parents",
  leave: "Leave applications",
  corrections: "Corrections with approval",
  registers: "Monthly registers",
  periods: "Period-wise attendance",
  reasons: "Absence reason codes",
  concerns: "Chronic-absence alerts",
  marks: "Marks entry",
  grading: "Grade scales",
  coscholastic: "Co-scholastic grades",
  report_cards: "Report cards",
  publish: "Publish results to parents",
  timetable: "Exam timetable",
  hall_tickets: "Hall tickets",
  moderation: "Marks approval (moderation)",
  analysis: "Result analysis",
  standard_certificates: "TC, bonafide, character & study certificates",
  issue_register: "Issue register",
  qr_verification: "QR verification",
  custom_templates: "Your own templates",
  approval: "Principal approval",
  parent_requests: "Requests from parents",
  homework_post: "Post homework",
  attachments: "Attachments",
  homework_alerts: "Homework alerts to parents",
  submissions: "Hand in online",
  rubrics: "Rubrics",
  feedback: "Marks & feedback",
  grid: "Class timetables",
  clash_check: "Teacher & room clash checks",
  timetable_parent_view: "Timetable in the parent portal",
  generator: "Timetable generator",
  substitutions: "Daily substitutions",
  syllabus: "Syllabus by unit and month",
  coverage: "Syllabus coverage meter",
  lesson_plans: "Lesson plans with review",
  observations: "Classroom observations",
};

const MODULE_LABEL: Record<string, string> = {
  WEBSITE: "School website",
  DISCLOSURE: "Mandatory public disclosure",
  ADMISSIONS_ENQUIRY: "Admissions enquiries",
  ADMISSIONS_CRM: "Admissions CRM",
  COMPLIANCE: "Compliance calendar",
  FRONT_OFFICE: "Front office",
  ENGAGEMENT: "PTM, helpdesk & surveys",
  COMMS: "Communication hub",
  SIS: "Student information",
  PORTAL: "Parent portal",
  ATTENDANCE: "Attendance & leave",
  EXAMS: "Exams & report cards",
  CERTIFICATES: "Certificates",
  HOMEWORK: "Homework",
  TIMETABLE: "Timetable",
  CURRICULUM: "Curriculum & lesson plans",
};

function formatValue(key: string, value: number) {
  if (key === "storage_bytes") {
    const gb = value / 1024 ** 3;
    return gb >= 1 ? `${gb.toFixed(gb >= 10 ? 0 : 1)} GB` : `${(value / 1024 ** 2).toFixed(1)} MB`;
  }
  return String(value);
}

export default async function PlanPage() {
  const session = await currentUser();
  if (!session) redirect("/login");
  const client = await api(session.accessToken);
  const { data } = await client.GET("/api/admin/v1/entitlements");
  if (!data) redirect("/dashboard");

  return (
    <div className="space-y-8">
      <section className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-ink-muted text-sm font-medium">Current plan</p>
          <h1 className="text-3xl font-bold tracking-tight">{data.planName ?? "No plan"}</h1>
        </div>
        {data.plan === "WEB_FREE" ? (
          <p className="inline-flex items-center gap-2 rounded-full bg-amber-50 px-4 py-2 text-sm font-medium text-amber-900 ring-1 ring-amber-200">
            <Sparkles className="size-4" aria-hidden /> Web Pro unlocks unlimited pages, 10 GB, two
            languages and your own domain.
          </p>
        ) : null}
      </section>

      <section
        aria-labelledby="usage-heading"
        className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-black/5"
      >
        <h2 id="usage-heading" className="text-lg font-semibold">
          Usage
        </h2>
        <ul className="mt-5 space-y-6">
          {(data.limits ?? []).map((l) => {
            const unlimited = l.limit === -1;
            const notIncluded = l.limit === 0;
            const pct = l.percent ?? 0;
            const tone =
              l.state === "BLOCKED"
                ? "bg-red-500"
                : l.state === "WARNING"
                  ? "bg-amber-500"
                  : "bg-indigo-500";
            return (
              <li key={`${l.module}-${l.key}`}>
                <div className="flex flex-wrap items-baseline justify-between gap-2 text-sm">
                  <span className="font-medium">{LIMIT_LABEL[l.key!] ?? l.key}</span>
                  <span className="text-ink-muted">
                    {notIncluded
                      ? "Not included in this plan"
                      : unlimited
                        ? `${formatValue(l.key!, l.used ?? 0)} used · unlimited`
                        : `${formatValue(l.key!, l.used ?? 0)} of ${formatValue(l.key!, l.limit ?? 0)}`}
                  </span>
                </div>
                {!unlimited && !notIncluded ? (
                  <div
                    className="mt-2 h-2.5 overflow-hidden rounded-full bg-black/5"
                    role="progressbar"
                    aria-label={LIMIT_LABEL[l.key!] ?? l.key}
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-valuenow={pct}
                  >
                    <div
                      className={`h-full rounded-full ${tone} transition-all`}
                      style={{ width: `${Math.max(pct, 2)}%` }}
                    />
                  </div>
                ) : null}
                {l.state === "WARNING" ? (
                  <p className="mt-1 text-xs text-amber-800">
                    You have used over 80% of this limit.
                  </p>
                ) : null}
                {l.state === "BLOCKED" && !notIncluded ? (
                  <p className="mt-1 text-xs text-red-700">
                    Limit reached — remove items or upgrade to add more.
                  </p>
                ) : null}
              </li>
            );
          })}
        </ul>
      </section>

      <section aria-labelledby="modules-heading">
        <h2 id="modules-heading" className="text-lg font-semibold">
          Included
        </h2>
        <div className="mt-4 grid gap-4 md:grid-cols-3">
          {(data.modules ?? []).map((m) => (
            <div key={m.module} className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-black/5">
              <p className="font-semibold">{MODULE_LABEL[m.module!] ?? m.module}</p>
              <p className="text-ink-muted text-xs tracking-wide uppercase">{m.depth}</p>
              <ul className="mt-3 space-y-1.5 text-sm">
                {(m.features ?? []).map((f) => (
                  <li key={f} className="flex items-center gap-2">
                    <CheckCircle2 className="size-4 text-emerald-600" aria-hidden />
                    {FEATURE_LABEL[f] ?? f}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
