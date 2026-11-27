"use client";

import { useCallback, useEffect, useState } from "react";

type Child = {
  id: string;
  name: string;
  admissionNo: string;
  classLabel?: string | null;
  classTeacher?: string | null;
  rollNo?: number | null;
  house?: string | null;
  medicalAlerts?: string | null;
};
type Me = { school: string; guardianName: string; phone: string; children: Child[] };
type Circular = {
  title: string;
  sentAt?: string | null;
  read: boolean;
  ackRequired: boolean;
  acknowledged: boolean;
  link: string;
};
type Req = { ticketNo: string; subject: string; status: string; updatedAt: string };
type Ptm = {
  title: string;
  date: string;
  time: string;
  teacher: string;
  room?: string | null;
  code: string;
};
type Prefs = {
  available: boolean;
  whatsapp: boolean;
  sms: boolean;
  email: boolean;
  hasEmail: boolean;
  language: string;
};

type ChildAttendance = {
  studentId: string;
  name: string;
  monthPercent?: number | null;
  yearPercent?: number | null;
  daysMarked: number;
  recent: { date: string; status: string; reason?: string | null }[];
};
type Leave = {
  id: string;
  studentId: string;
  studentName: string;
  fromDate: string;
  toDate: string;
  days: number;
  reason: string;
  status: "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";
  decisionNote?: string | null;
};

type ReportCard = {
  student: { id: string; name: string; classLabel: string };
  exams: { examId: string; name: string; weight: number }[];
  subjects: {
    subject: string;
    cells: ({
      total?: number | null;
      max: number;
      grade?: string | null;
      code?: string | null;
    } | null)[];
    weightedPercent?: number | null;
    weightedGrade?: string | null;
  }[];
  coScholastic: { subject: string; grades: Record<string, string> }[];
  totals: {
    examId: string;
    percent?: number | null;
    grade?: string | null;
    passed?: boolean | null;
    rank?: number | null;
    of?: number | null;
    remark?: string | null;
  }[];
  weightedPercent?: number | null;
  weightedGrade?: string | null;
  attendancePercent?: number | null;
};

type Cert = {
  id: string;
  title: string;
  number?: string | null;
  status: "REQUESTED" | "PENDING_APPROVAL" | "ISSUED" | "CANCELLED" | "REJECTED";
  studentName: string;
  purpose?: string | null;
  statusNote?: string | null;
  verifyUrl?: string | null;
};
type Certs = { types: { templateId: string; name: string }[]; certificates: Cert[] };

const MARK: Record<string, string> = {
  A: "Absent",
  L: "Late",
  H: "Half day",
  LV: "On leave",
};
const LEAVE_STATUS: Record<Leave["status"], string> = {
  PENDING: "Waiting for the class teacher",
  APPROVED: "Approved",
  REJECTED: "Not approved",
  CANCELLED: "Withdrawn",
};

const REQ_STATUS: Record<string, string> = {
  OPEN: "Received",
  IN_PROGRESS: "Being worked on",
  WAITING: "Waiting for you",
  RESOLVED: "Resolved",
  CLOSED: "Closed",
};
const ist = (o: Intl.DateTimeFormatOptions) =>
  new Intl.DateTimeFormat("en-IN", { timeZone: "Asia/Kolkata", ...o });
const day = (iso: string) => ist({ day: "numeric", month: "short" }).format(new Date(iso));
const dateLong = (d: string) =>
  ist({ weekday: "short", day: "numeric", month: "short" }).format(new Date(`${d}T00:00:00+05:30`));
const time = (t: string) => {
  const [h, m] = t.split(":").map(Number);
  return new Intl.DateTimeFormat("en-IN", { hour: "numeric", minute: "2-digit" }).format(
    new Date(2000, 0, 1, h, m),
  );
};

async function call<T>(
  path: string,
  method = "GET",
  body?: unknown,
): Promise<{ ok: boolean; status: number; data?: T; message?: string }> {
  try {
    const res = await fetch(`/api/portal/${path}`, {
      method,
      headers: { "Content-Type": "application/json", "X-Portal": "1" },
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: "no-store",
    });
    const data = (await res.json().catch(() => undefined)) as (T & { detail?: string }) | undefined;
    if (res.ok) return { ok: true, status: res.status, data };
    return {
      ok: false,
      status: res.status,
      message:
        res.status === 429
          ? "Too many attempts. Please wait 15 minutes and try again."
          : (data?.detail ?? "Something went wrong. Please try again."),
    };
  } catch {
    return {
      ok: false,
      status: 0,
      message: "We could not reach the school. Please check your connection.",
    };
  }
}

// ------------------------------------------------------------------ sign-in & setup

export function ParentSetup({ code }: { code: string }) {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.currentTarget).entries()) as Record<string, string>;
    if (f.pin !== f.pin2) {
      setError("The two PINs don't match.");
      return;
    }
    setBusy(true);
    setError(null);
    const r = await call("setup", "POST", { code, phone: f.phone, pin: f.pin });
    setBusy(false);
    if (r.ok) window.location.href = "/parent";
    else setError(r.message ?? null);
  }
  return (
    <form onSubmit={(e) => void submit(e)} className="sb-card mx-auto grid max-w-md gap-5 p-6">
      <div>
        <h2 className="text-2xl font-bold">Set up your parent account</h2>
        <p className="sb-muted mt-1 text-sm">
          Confirm your mobile number and choose a PIN. You will use them to sign in.
        </p>
      </div>
      <label className="block">
        <span className="mb-1.5 block text-sm font-semibold">Mobile number</span>
        <input
          name="phone"
          required
          type="tel"
          inputMode="tel"
          autoComplete="tel"
          maxLength={20}
          className="sb-input"
        />
      </label>
      <label className="block">
        <span className="mb-1.5 block text-sm font-semibold">Choose a PIN (4–6 digits)</span>
        <input
          name="pin"
          required
          type="password"
          inputMode="numeric"
          pattern="[0-9]{4,6}"
          autoComplete="new-password"
          maxLength={6}
          className="sb-input"
        />
      </label>
      <label className="block">
        <span className="mb-1.5 block text-sm font-semibold">Repeat the PIN</span>
        <input
          name="pin2"
          required
          type="password"
          inputMode="numeric"
          pattern="[0-9]{4,6}"
          autoComplete="new-password"
          maxLength={6}
          className="sb-input"
        />
      </label>
      {error && (
        <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
          {error}
        </p>
      )}
      <button
        type="submit"
        disabled={busy}
        className="sb-btn sb-btn-primary sb-focus disabled:opacity-60"
      >
        {busy ? "Setting up…" : "Create account"}
      </button>
    </form>
  );
}

function SignIn({ onDone }: { onDone: () => void }) {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.currentTarget).entries()) as Record<string, string>;
    setBusy(true);
    setError(null);
    const r = await call("login", "POST", { phone: f.phone, pin: f.pin });
    setBusy(false);
    if (r.ok) onDone();
    else setError(r.message ?? null);
  }
  return (
    <form onSubmit={(e) => void submit(e)} className="sb-card mx-auto grid max-w-md gap-5 p-6">
      <div>
        <h2 className="text-2xl font-bold">Parent sign in</h2>
        <p className="sb-muted mt-1 text-sm">
          Use the mobile number the school has for you and your PIN.
        </p>
      </div>
      <label className="block">
        <span className="mb-1.5 block text-sm font-semibold">Mobile number</span>
        <input
          name="phone"
          required
          type="tel"
          inputMode="tel"
          autoComplete="tel"
          maxLength={20}
          className="sb-input"
        />
      </label>
      <label className="block">
        <span className="mb-1.5 block text-sm font-semibold">PIN</span>
        <input
          name="pin"
          required
          type="password"
          inputMode="numeric"
          autoComplete="current-password"
          maxLength={6}
          className="sb-input"
        />
      </label>
      {error && (
        <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
          {error}
        </p>
      )}
      <button
        type="submit"
        disabled={busy}
        className="sb-btn sb-btn-primary sb-focus disabled:opacity-60"
      >
        {busy ? "Signing in…" : "Sign in"}
      </button>
      <p className="sb-muted text-sm">
        New here or forgot your PIN? Ask the school office for your personal invitation link.
      </p>
    </form>
  );
}

// ------------------------------------------------------------------ dashboard

export function ParentPortal() {
  const [me, setMe] = useState<Me | null | "signed-out">(null);
  const [circulars, setCirculars] = useState<Circular[]>([]);
  const [requests, setRequests] = useState<Req[]>([]);
  const [ptm, setPtm] = useState<Ptm[]>([]);
  const [prefs, setPrefs] = useState<Prefs | null>(null);
  const [attendance, setAttendance] = useState<ChildAttendance[]>([]);
  const [leave, setLeave] = useState<Leave[]>([]);
  const [cards, setCards] = useState<ReportCard[]>([]);
  const [certs, setCerts] = useState<Certs | null>(null);
  const [homework, setHomework] = useState<Hw[]>([]);
  const [timetables, setTimetables] = useState<ChildTt[]>([]);
  const [early, setEarly] = useState<EyFeed[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const r = await call<Me>("me");
    if (!r.ok) {
      if (r.status === 401) setMe("signed-out");
      else setError(r.message ?? null);
      return;
    }
    setMe(r.data!);
    const [c, q, p, pr, at, lv, rc, ce, hw, tt, ey] = await Promise.all([
      call<Circular[]>("circulars"),
      call<Req[]>("requests"),
      call<Ptm[]>("ptm"),
      call<Prefs>("preferences"),
      call<ChildAttendance[]>("attendance"),
      call<Leave[]>("leave"),
      call<ReportCard[]>("report-cards"),
      call<Certs>("certificates"),
      call<Hw[]>("homework"),
      call<ChildTt[]>("timetable"),
      call<EyFeed[]>("early-years"),
    ]);
    setEarly(ey.data ?? []);
    setTimetables(tt.data ?? []);
    setHomework(hw.data ?? []);
    setCerts(ce.data ?? null);
    setCards(rc.data ?? []);
    setAttendance(at.data ?? []);
    setLeave(lv.data ?? []);
    setCirculars(c.data ?? []);
    setRequests(q.data ?? []);
    setPtm(p.data ?? []);
    setPrefs(pr.data ?? null);
  }, []);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  if (error)
    return (
      <p
        role="alert"
        className="mx-auto max-w-md rounded-xl bg-red-50 px-4 py-3 font-medium text-red-700"
      >
        {error}
      </p>
    );
  if (me === null) return <p className="sb-muted mx-auto max-w-md">Loading…</p>;
  if (me === "signed-out")
    return (
      <SignIn
        onDone={() => {
          setMe(null);
          void load();
        }}
      />
    );

  async function logout() {
    await call("logout", "POST");
    setMe("signed-out");
  }
  const unread = circulars.filter((c) => !c.read || (c.ackRequired && !c.acknowledged)).length;

  return (
    <div className="grid gap-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="sb-muted text-sm">{me.school}</p>
          <h2 className="text-2xl font-bold">Hello, {me.guardianName.split(" ")[0]}</h2>
        </div>
        <button
          type="button"
          className="sb-btn sb-btn-ghost text-primary"
          onClick={() => void logout()}
        >
          Sign out
        </button>
      </div>

      <section aria-labelledby="kids">
        <h3 id="kids" className="mb-3 text-lg font-bold">
          Your children
        </h3>
        {me.children.length === 0 ? (
          <p className="sb-muted">
            No enrolled children are linked to your number yet. Please contact the school office.
          </p>
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2">
            {me.children.map((c) => (
              <li key={c.id} className="sb-card p-5">
                <p className="text-lg font-bold">{c.name}</p>
                <p className="sb-muted text-sm">
                  {[c.classLabel, c.rollNo != null ? `Roll ${c.rollNo}` : null, c.admissionNo]
                    .filter(Boolean)
                    .join(" · ")}
                </p>
                {c.classTeacher && <p className="mt-2 text-sm">Class teacher: {c.classTeacher}</p>}
                {c.house && <p className="text-sm">House: {c.house}</p>}
                {c.medicalAlerts && (
                  <p className="mt-2 rounded-xl bg-amber-50 px-3 py-2 text-sm text-amber-900">
                    Medical note on record: {c.medicalAlerts}
                  </p>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      {early.length > 0 && <EarlyYearsSection items={early} />}

      {timetables.length > 0 && <TimetableSection items={timetables} />}

      {homework.length > 0 && (
        <HomeworkSection
          items={homework}
          onChanged={async () => setHomework((await call<Hw[]>("homework")).data ?? [])}
        />
      )}

      {me.children.length > 0 && attendance.length > 0 && (
        <AttendanceSection
          kids={me.children}
          attendance={attendance}
          leave={leave}
          onChanged={async () => {
            const [at, lv] = await Promise.all([
              call<ChildAttendance[]>("attendance"),
              call<Leave[]>("leave"),
            ]);
            setAttendance(at.data ?? []);
            setLeave(lv.data ?? []);
          }}
        />
      )}

      {cards.length > 0 && <ReportCards cards={cards} />}

      {certs &&
        me.children.length > 0 &&
        (certs.types.length > 0 || certs.certificates.length > 0) && (
          <CertificatesSection
            kids={me.children}
            data={certs}
            onChanged={async () => setCerts((await call<Certs>("certificates")).data ?? null)}
          />
        )}

      <section aria-labelledby="circ">
        <h3 id="circ" className="mb-3 text-lg font-bold">
          Circulars{" "}
          {unread > 0 && (
            <span className="bg-primary text-on-primary ml-1 rounded-full px-2 py-0.5 text-sm">
              {unread} new
            </span>
          )}
        </h3>
        {circulars.length === 0 ? (
          <p className="sb-muted">No circulars yet.</p>
        ) : (
          <ul className="sb-card divide-y divide-black/5">
            {circulars.map((c) => (
              <li key={c.link}>
                <a
                  href={c.link}
                  className="flex flex-wrap items-center gap-x-3 gap-y-1 px-4 py-3 hover:bg-black/[0.02]"
                >
                  <span className="min-w-[12rem] flex-1">
                    <span className={`block ${c.read ? "" : "font-bold"}`}>{c.title}</span>
                    {c.sentAt && <span className="sb-muted text-xs">{day(c.sentAt)}</span>}
                  </span>
                  {c.ackRequired && !c.acknowledged && (
                    <span className="rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-semibold text-amber-900">
                      Please acknowledge
                    </span>
                  )}
                  {c.acknowledged && (
                    <span className="text-xs font-semibold text-emerald-700">✓ Acknowledged</span>
                  )}
                </a>
              </li>
            ))}
          </ul>
        )}
      </section>

      <div className="grid gap-8 lg:grid-cols-2">
        <section aria-labelledby="ptm">
          <h3 id="ptm" className="mb-3 text-lg font-bold">
            Parent-teacher meetings
          </h3>
          {ptm.length === 0 ? (
            <p className="sb-muted">
              No upcoming bookings.{" "}
              <a href="/ptm" className="text-primary font-semibold underline">
                Book a slot
              </a>
            </p>
          ) : (
            <ul className="sb-card divide-y divide-black/5">
              {ptm.map((p) => (
                <li key={p.code} className="px-4 py-3">
                  <p className="font-semibold">
                    {dateLong(p.date)} · {time(p.time)}
                  </p>
                  <p className="sb-muted text-sm">
                    {p.teacher}
                    {p.room ? ` · ${p.room}` : ""} · code {p.code}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </section>
        <section aria-labelledby="req">
          <h3 id="req" className="mb-3 text-lg font-bold">
            Your requests
          </h3>
          {requests.length === 0 ? (
            <p className="sb-muted">
              No requests.{" "}
              <a href="/help" className="text-primary font-semibold underline">
                Ask the school
              </a>
            </p>
          ) : (
            <ul className="sb-card divide-y divide-black/5">
              {requests.map((r) => (
                <li key={r.ticketNo} className="flex flex-wrap items-center gap-x-3 px-4 py-3">
                  <span className="min-w-[10rem] flex-1">
                    <span className="block font-semibold">{r.subject}</span>
                    <span className="sb-muted text-xs">
                      {r.ticketNo} · {day(r.updatedAt)}
                    </span>
                  </span>
                  <span className="text-sm">{REQ_STATUS[r.status] ?? r.status}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      {prefs?.available && <PreferencesCard prefs={prefs} onSaved={setPrefs} />}
    </div>
  );
}

const todayIst = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date());

function AttendanceSection({
  kids,
  attendance,
  leave,
  onChanged,
}: {
  kids: Child[];
  attendance: ChildAttendance[];
  leave: Leave[];
  onChanged: () => Promise<void>;
}) {
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null);
  const [busy, setBusy] = useState(false);
  async function apply(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const f = new FormData(form);
    setBusy(true);
    const r = await call<Leave>("leave", "POST", {
      studentId: f.get("child"),
      fromDate: f.get("from"),
      toDate: f.get("to") || f.get("from"),
      reason: f.get("reason"),
    });
    setBusy(false);
    if (r.ok) {
      form.reset();
      await onChanged();
      setMsg({ text: "Sent to the class teacher. You will see the decision here.", ok: true });
    } else setMsg({ text: r.message ?? "Could not send.", ok: false });
  }
  async function cancel(id: string) {
    const r = await call<Leave>(`leave/${id}/cancel`, "POST");
    if (r.ok) await onChanged();
    else setMsg({ text: r.message ?? "Could not withdraw.", ok: false });
  }
  const pct = (v?: number | null) => (v == null ? "–" : `${v}%`);
  return (
    <section aria-labelledby="att" className="grid gap-4">
      <h3 id="att" className="text-lg font-bold">
        Attendance &amp; leave
      </h3>
      <ul className="grid gap-3 sm:grid-cols-2">
        {attendance.map((a) => (
          <li key={a.studentId} className="sb-card p-5">
            <p className="font-bold">{a.name}</p>
            <p className="mt-1 flex flex-wrap gap-x-5 text-sm">
              <span>
                This month: <strong>{pct(a.monthPercent)}</strong>
              </span>
              <span>
                This year: <strong>{pct(a.yearPercent)}</strong>
              </span>
            </p>
            {a.recent.length > 0 ? (
              <ul className="sb-muted mt-2 text-sm">
                {a.recent.slice(0, 5).map((r) => (
                  <li key={r.date}>
                    {dateLong(r.date)}: {MARK[r.status] ?? r.status}
                    {r.reason ? ` (${r.reason})` : ""}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="sb-muted mt-2 text-sm">
                {a.daysMarked > 0 ? "No absences recently." : "No attendance recorded yet."}
              </p>
            )}
          </li>
        ))}
      </ul>
      {leave.length > 0 && (
        <ul className="sb-card divide-y divide-black/5">
          {leave.map((l) => (
            <li key={l.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-4 py-3">
              <span className="min-w-[12rem] flex-1">
                <span className="block font-semibold">
                  {l.studentName}: {dateLong(l.fromDate)}
                  {l.toDate !== l.fromDate ? ` – ${dateLong(l.toDate)}` : ""}
                </span>
                <span className="sb-muted text-xs">
                  {l.reason}
                  {l.decisionNote ? ` · School: ${l.decisionNote}` : ""}
                </span>
              </span>
              <span className="text-sm">{LEAVE_STATUS[l.status]}</span>
              {(l.status === "PENDING" || (l.status === "APPROVED" && l.fromDate > todayIst())) && (
                <button
                  type="button"
                  className="sb-btn sb-btn-ghost text-primary text-sm"
                  onClick={() => void cancel(l.id)}
                >
                  Withdraw
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      <form onSubmit={apply} className="sb-card grid gap-3 p-5 sm:grid-cols-2">
        <p className="font-bold sm:col-span-2">Apply for leave</p>
        <label className="block sm:col-span-2">
          <span className="mb-1.5 block text-sm font-semibold">Child</span>
          <select name="child" className="sb-input" required>
            {kids.map((k) => (
              <option key={k.id} value={k.id}>
                {k.name}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="mb-1.5 block text-sm font-semibold">From</span>
          <input type="date" name="from" className="sb-input" required />
        </label>
        <label className="block">
          <span className="mb-1.5 block text-sm font-semibold">To (optional)</span>
          <input type="date" name="to" className="sb-input" />
        </label>
        <label className="block sm:col-span-2">
          <span className="mb-1.5 block text-sm font-semibold">Reason</span>
          <input name="reason" className="sb-input" required maxLength={500} />
        </label>
        <div className="flex flex-wrap items-center gap-3 sm:col-span-2">
          <button type="submit" className="sb-btn sb-btn-primary sb-focus" disabled={busy}>
            {busy ? "Sending…" : "Send to class teacher"}
          </button>
          {msg && (
            <span
              role={msg.ok ? "status" : "alert"}
              className={`text-sm ${msg.ok ? "" : "font-semibold text-red-700"}`}
            >
              {msg.text}
            </span>
          )}
        </div>
      </form>
    </section>
  );
}

function ReportCards({ cards }: { cards: ReportCard[] }) {
  return (
    <section aria-labelledby="rc" className="grid gap-4">
      <h3 id="rc" className="text-lg font-bold">
        Results &amp; report cards
      </h3>
      {cards.map((r) => {
        const weighted = r.exams.some((e) => e.weight > 0);
        return (
          <details key={r.student.id} className="sb-card p-5" open={cards.length === 1}>
            <summary className="cursor-pointer">
              <span className="font-bold">{r.student.name}</span>
              <span className="sb-muted text-sm">
                {" "}
                · {r.student.classLabel} ·{" "}
                {r.totals
                  .map((t) => {
                    const e = r.exams.find((x) => x.examId === t.examId);
                    return `${e?.name}: ${t.percent != null ? `${t.percent}% (${t.grade})` : "–"}`;
                  })
                  .join(" · ")}
              </span>
            </summary>
            <div className="mt-3 overflow-x-auto">
              <table className="w-full min-w-[28rem] border-collapse text-sm">
                <thead>
                  <tr className="sb-muted border-b border-black/10 text-left text-xs">
                    <th scope="col" className="py-1.5 pr-2 font-semibold">
                      Subject
                    </th>
                    {r.exams.map((e) => (
                      <th
                        key={e.examId}
                        scope="col"
                        className="px-2 py-1.5 text-center font-semibold"
                      >
                        {e.name}
                      </th>
                    ))}
                    {weighted && (
                      <th scope="col" className="px-2 py-1.5 text-center font-semibold">
                        Year
                      </th>
                    )}
                  </tr>
                </thead>
                <tbody>
                  {r.subjects.map((s) => (
                    <tr key={s.subject} className="border-b border-black/5">
                      <th scope="row" className="py-1.5 pr-2 text-left font-medium">
                        {s.subject}
                      </th>
                      {s.cells.map((c, i) => (
                        <td key={i} className="px-2 py-1.5 text-center tabular-nums">
                          {!c
                            ? "–"
                            : c.code
                              ? c.code
                              : c.total == null
                                ? ""
                                : `${c.total}/${c.max} (${c.grade})`}
                        </td>
                      ))}
                      {weighted && (
                        <td className="px-2 py-1.5 text-center font-semibold tabular-nums">
                          {s.weightedPercent != null
                            ? `${s.weightedPercent}% (${s.weightedGrade})`
                            : "–"}
                        </td>
                      )}
                    </tr>
                  ))}
                  <tr className="font-bold">
                    <th scope="row" className="py-1.5 pr-2 text-left">
                      Overall
                    </th>
                    {r.totals.map((t) => (
                      <td key={t.examId} className="px-2 py-1.5 text-center tabular-nums">
                        {t.percent != null ? `${t.percent}% (${t.grade})` : "–"}
                        {t.rank != null && (
                          <span className="block text-xs font-normal">
                            Rank {t.rank} of {t.of}
                          </span>
                        )}
                      </td>
                    ))}
                    {weighted && (
                      <td className="px-2 py-1.5 text-center tabular-nums">
                        {r.weightedPercent != null
                          ? `${r.weightedPercent}% (${r.weightedGrade})`
                          : "–"}
                      </td>
                    )}
                  </tr>
                </tbody>
              </table>
            </div>
            {r.coScholastic.length > 0 && (
              <p className="mt-3 text-sm">
                <span className="font-semibold">Co-scholastic: </span>
                {r.coScholastic
                  .map((c) => `${c.subject} ${Object.values(c.grades).join("/")}`)
                  .join(" · ")}
              </p>
            )}
            {r.totals
              .filter((t) => t.remark)
              .map((t) => (
                <p key={t.examId} className="mt-2 text-sm">
                  <span className="font-semibold">Class teacher: </span>“{t.remark}”
                </p>
              ))}
            {r.attendancePercent != null && (
              <p className="mt-2 text-sm">
                <span className="font-semibold">Attendance this year: </span>
                {r.attendancePercent}%
              </p>
            )}
          </details>
        );
      })}
    </section>
  );
}

const CERT_STATUS: Record<Cert["status"], string> = {
  REQUESTED: "Requested",
  PENDING_APPROVAL: "Being prepared",
  ISSUED: "Ready",
  CANCELLED: "Cancelled",
  REJECTED: "Not issued",
};

type CertView = {
  certificate: Cert & { body?: string | null; issuedAt?: string | null };
  school: string;
  signatoryName?: string | null;
  signatoryTitle: string;
};

function CertificatesSection({
  kids,
  data,
  onChanged,
}: {
  kids: Child[];
  data: Certs;
  onChanged: () => Promise<void>;
}) {
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null);
  const [busy, setBusy] = useState(false);
  const [view, setView] = useState<CertView | null>(null);
  async function request(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const f = new FormData(form);
    setBusy(true);
    const r = await call<Cert>("certificates", "POST", {
      studentId: f.get("child"),
      templateId: f.get("type"),
      purpose: f.get("purpose"),
    });
    setBusy(false);
    if (r.ok) {
      form.reset();
      await onChanged();
      setMsg({ text: "Request sent. The school will let you know when it is ready.", ok: true });
    } else setMsg({ text: r.message ?? "Could not send.", ok: false });
  }
  async function open(id: string) {
    const r = await call<CertView>(`certificates/${id}`);
    if (r.ok && r.data) setView(r.data);
  }
  return (
    <section aria-labelledby="certs" className="grid gap-4">
      <h3 id="certs" className="text-lg font-bold">
        Certificates
      </h3>
      {data.certificates.length > 0 && (
        <ul className="sb-card divide-y divide-black/5">
          {data.certificates.map((c) => (
            <li key={c.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-4 py-3">
              <span className="min-w-[12rem] flex-1">
                <span className="block font-semibold">
                  {c.title} · {c.studentName}
                </span>
                <span className="sb-muted text-xs">
                  {c.number ? `${c.number} · ` : ""}
                  {c.purpose ?? ""}
                  {c.statusNote && c.status === "REJECTED" ? ` · School: ${c.statusNote}` : ""}
                </span>
              </span>
              <span className="text-sm">{CERT_STATUS[c.status]}</span>
              {c.status === "ISSUED" && (
                <button
                  type="button"
                  className="sb-btn sb-btn-ghost text-primary text-sm"
                  onClick={() => void open(c.id)}
                >
                  View
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      {view && (
        <div className="sb-card p-5" role="region" aria-label={view.certificate.title}>
          <p className="sb-muted text-xs">{view.school}</p>
          <p className="text-lg font-bold">
            {view.certificate.title} · {view.certificate.number}
          </p>
          <p className="mt-3 text-sm whitespace-pre-line">{view.certificate.body}</p>
          <p className="mt-3 text-sm">
            {view.signatoryName ? `${view.signatoryName}, ` : ""}
            {view.signatoryTitle}
          </p>
          {view.certificate.verifyUrl && (
            <p className="sb-muted mt-2 text-xs">
              Anyone can check it at{" "}
              <a className="text-primary underline" href={view.certificate.verifyUrl}>
                {view.certificate.verifyUrl}
              </a>
              . Collect the signed and sealed copy from the school office.
            </p>
          )}
          <button
            type="button"
            className="sb-btn sb-btn-ghost text-primary mt-2 text-sm"
            onClick={() => setView(null)}
          >
            Close
          </button>
        </div>
      )}
      {data.types.length > 0 && (
        <form onSubmit={request} className="sb-card grid gap-3 p-5 sm:grid-cols-2">
          <p className="font-bold sm:col-span-2">Ask for a certificate</p>
          <label className="block">
            <span className="mb-1.5 block text-sm font-semibold">Certificate</span>
            <select name="type" className="sb-input" required>
              {data.types.map((t) => (
                <option key={t.templateId} value={t.templateId}>
                  {t.name}
                </option>
              ))}
            </select>
          </label>
          <label className="block">
            <span className="mb-1.5 block text-sm font-semibold">For</span>
            <select name="child" className="sb-input" required>
              {kids.map((k) => (
                <option key={k.id} value={k.id}>
                  {k.name}
                </option>
              ))}
            </select>
          </label>
          <label className="block sm:col-span-2">
            <span className="mb-1.5 block text-sm font-semibold">Purpose</span>
            <input
              name="purpose"
              className="sb-input"
              required
              maxLength={200}
              placeholder="e.g. passport application"
            />
          </label>
          <div className="flex flex-wrap items-center gap-3 sm:col-span-2">
            <button type="submit" className="sb-btn sb-btn-primary sb-focus" disabled={busy}>
              {busy ? "Sending…" : "Send request"}
            </button>
            {msg && (
              <span
                role={msg.ok ? "status" : "alert"}
                className={`text-sm ${msg.ok ? "" : "font-semibold text-red-700"}`}
              >
                {msg.text}
              </span>
            )}
          </div>
        </form>
      )}
    </section>
  );
}

type HwFile = { id: string; name: string; sizeBytes: number };
type HwSubmission = {
  status: "SUBMITTED" | "RETURNED" | "GRADED";
  text?: string | null;
  fileName?: string | null;
  late: boolean;
  points?: number | null;
  feedback?: string | null;
};
type Hw = {
  id: string;
  studentId: string;
  studentName: string;
  subject?: string | null;
  title: string;
  details?: string | null;
  dueOn: string;
  files: HwFile[];
  allowSubmissions: boolean;
  closed: boolean;
  maxPoints?: number | null;
  submission?: HwSubmission | null;
};

function HomeworkSection({ items, onChanged }: { items: Hw[]; onChanged: () => Promise<void> }) {
  const [open, setOpen] = useState<string | null>(null);
  const [msg, setMsg] = useState<{ key: string; text: string; ok: boolean } | null>(null);
  const [busy, setBusy] = useState(false);
  const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date());
  async function file(id: string) {
    const r = await call<{ url: string }>(`homework/files/${id}`);
    if (r.ok && r.data) window.open(r.data.url, "_blank", "noopener");
  }
  async function submit(h: Hw, e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const key = h.id + h.studentId;
    const form = new FormData(e.currentTarget);
    const f = form.get("file");
    if (f instanceof File && f.size === 0) form.delete("file");
    setBusy(true);
    try {
      const res = await fetch(`/api/portal-upload/homework/${h.id}?studentId=${h.studentId}`, {
        method: "POST",
        headers: { "X-Portal": "1" },
        body: form,
      });
      const data = (await res.json().catch(() => ({}))) as { detail?: string };
      if (res.ok) {
        await onChanged();
        setMsg({ key, text: "Handed in. The teacher will see it.", ok: true });
      } else setMsg({ key, text: data.detail ?? "Could not hand in.", ok: false });
    } catch {
      setMsg({ key, text: "We could not reach the school. Please try again.", ok: false });
    } finally {
      setBusy(false);
    }
  }
  return (
    <section aria-labelledby="hw" className="grid gap-3">
      <h3 id="hw" className="text-lg font-bold">
        Homework
      </h3>
      <ul className="sb-card divide-y divide-black/5">
        {items.map((h) => {
          const key = h.id + h.studentId;
          const s = h.submission;
          return (
            <li key={key} className="px-4 py-3">
              <button
                type="button"
                className="flex w-full flex-wrap items-center gap-x-3 gap-y-1 text-left"
                aria-expanded={open === key}
                onClick={() => setOpen(open === key ? null : key)}
              >
                <span className="min-w-[12rem] flex-1">
                  <span className="block font-semibold">{h.title}</span>
                  <span className="sb-muted text-xs">
                    {h.studentName}
                    {h.subject ? ` · ${h.subject}` : ""}
                  </span>
                </span>
                <span className={`text-sm ${h.dueOn < today && !s ? "text-red-700" : ""}`}>
                  Due {dateLong(h.dueOn)}
                </span>
                {s && (
                  <span className="rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold text-emerald-800">
                    {s.status === "GRADED"
                      ? `Marked${s.points != null ? ` ${s.points}${h.maxPoints ? `/${h.maxPoints}` : ""}` : ""}`
                      : s.status === "RETURNED"
                        ? "Redo asked"
                        : "Handed in"}
                  </span>
                )}
              </button>
              {open === key && (
                <div className="mt-3 grid gap-3 text-sm">
                  {h.details && <p className="whitespace-pre-line">{h.details}</p>}
                  {h.files.length > 0 && (
                    <div className="flex flex-wrap gap-2">
                      {h.files.map((f) => (
                        <button
                          key={f.id}
                          type="button"
                          className="sb-btn sb-btn-ghost text-primary text-sm"
                          onClick={() => void file(f.id)}
                        >
                          📎 {f.name}
                        </button>
                      ))}
                    </div>
                  )}
                  {s?.feedback && (
                    <p className="rounded-xl bg-amber-50 px-3 py-2">
                      <span className="font-semibold">Teacher: </span>
                      {s.feedback}
                    </p>
                  )}
                  {h.allowSubmissions && s?.status !== "GRADED" && !h.closed && (
                    <form onSubmit={(e) => void submit(h, e)} className="grid gap-2">
                      <label className="block">
                        <span className="mb-1.5 block font-semibold">
                          {s ? "Hand in again" : "Hand in"} (answer)
                        </span>
                        <textarea
                          name="text"
                          rows={3}
                          maxLength={4000}
                          className="sb-input"
                          defaultValue={s?.text ?? ""}
                        />
                      </label>
                      <label className="block">
                        <span className="mb-1.5 block font-semibold">
                          Photo or PDF (optional, 8 MB)
                        </span>
                        <input
                          name="file"
                          type="file"
                          accept="image/jpeg,image/png,application/pdf"
                          className="text-sm"
                        />
                      </label>
                      <div className="flex flex-wrap items-center gap-3">
                        <button
                          type="submit"
                          className="sb-btn sb-btn-primary sb-focus"
                          disabled={busy}
                        >
                          {busy ? "Sending…" : "Hand in"}
                        </button>
                        {msg?.key === key && (
                          <span
                            role={msg.ok ? "status" : "alert"}
                            className={msg.ok ? "" : "font-semibold text-red-700"}
                          >
                            {msg.text}
                          </span>
                        )}
                      </div>
                    </form>
                  )}
                  {h.allowSubmissions && h.closed && !s && (
                    <p className="sb-muted">The due date has passed; please talk to the teacher.</p>
                  )}
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}

type TtPeriod = { label: string; start: string; end: string; break: boolean };
type TtSlot = {
  day: number;
  period: number;
  subject?: string | null;
  label?: string | null;
  teacher?: string | null;
  room?: string | null;
};
type ChildTt = {
  studentId: string;
  name: string;
  classLabel: string;
  days: number[];
  periods: TtPeriod[];
  slots: TtSlot[];
  substitutionsToday: TtSlot[];
};
const TT_DAY = ["", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

function TimetableSection({ items }: { items: ChildTt[] }) {
  const today = ((new Date().getDay() + 6) % 7) + 1;
  return (
    <section aria-labelledby="tt" className="grid gap-3">
      <h3 id="tt" className="text-lg font-bold">
        Timetable
      </h3>
      {items.map((c) => {
        const todays = c.periods
          .map((p, i) => ({ p, i, s: c.slots.find((x) => x.day === today && x.period === i) }))
          .filter((x) => !x.p.break && x.s);
        return (
          <details key={c.studentId} className="sb-card p-5">
            <summary className="cursor-pointer">
              <span className="font-bold">{c.name}</span>
              <span className="sb-muted text-sm"> · {c.classLabel} · today and this week</span>
            </summary>
            {c.substitutionsToday.length > 0 && (
              <p className="mt-3 rounded-xl bg-amber-50 px-3 py-2 text-sm text-amber-900">
                Today:{" "}
                {c.substitutionsToday
                  .map(
                    (s) =>
                      `${c.periods[s.period]?.label} taken by ${s.teacher ?? "another teacher"}`,
                  )
                  .join("; ")}
              </p>
            )}
            {c.days.includes(today) && todays.length > 0 && (
              <ul className="mt-3 grid gap-1 text-sm">
                {todays.map(({ p, i, s }) => (
                  <li key={i}>
                    <span className="sb-muted">{p.start}</span> {s?.subject ?? s?.label}
                    {s?.teacher ? ` · ${s.teacher}` : ""}
                  </li>
                ))}
              </ul>
            )}
            <div className="mt-3 overflow-x-auto">
              <table className="w-full min-w-[34rem] border-collapse text-xs">
                <thead>
                  <tr className="sb-muted">
                    <th scope="col" className="py-1 pr-2 text-left font-semibold">
                      Period
                    </th>
                    {c.days.map((d) => (
                      <th key={d} scope="col" className="px-1 py-1 text-left font-semibold">
                        {TT_DAY[d]}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {c.periods.map((p, i) =>
                    p.break ? null : (
                      <tr key={i} className="border-t border-black/5">
                        <th scope="row" className="py-1 pr-2 text-left font-medium">
                          {p.start}
                        </th>
                        {c.days.map((d) => {
                          const s = c.slots.find((x) => x.day === d && x.period === i);
                          return (
                            <td key={d} className="px-1 py-1">
                              {s?.subject ?? s?.label ?? ""}
                            </td>
                          );
                        })}
                      </tr>
                    ),
                  )}
                </tbody>
              </table>
            </div>
          </details>
        );
      })}
    </section>
  );
}

type EyObs = {
  id: string;
  observedOn: string;
  domain?: string | null;
  note: string;
  photoUrl?: string | null;
};
type EyFeed = {
  studentId: string;
  name: string;
  today?: {
    arrival?: string | null;
    meals: Record<string, string>;
    napFrom?: string | null;
    napTo?: string | null;
    toilet?: number | null;
    mood?: string | null;
    activities?: string | null;
    note?: string | null;
  } | null;
  observations: EyObs[];
  pickups: { id: string; name: string; relation?: string | null }[];
  achieved: number;
  total: number;
};
const EY_MOOD: Record<string, string> = {
  HAPPY: "😊 Happy",
  CALM: "🙂 Calm",
  TIRED: "😴 Tired",
  UPSET: "😢 Upset",
};
const EY_MEAL: Record<string, string> = {
  ALL: "ate all",
  MOST: "ate most",
  SOME: "ate some",
  NONE: "did not eat",
};

function EarlyYearsSection({ items }: { items: EyFeed[] }) {
  return (
    <section aria-labelledby="ey" className="grid gap-3">
      <h3 id="ey" className="text-lg font-bold">
        Today at school
      </h3>
      {items.map((c) => (
        <div key={c.studentId} className="sb-card grid gap-3 p-5">
          <p className="font-bold">
            {c.name}
            <span className="sb-muted text-sm font-normal">
              {" "}
              · {c.achieved} of {c.total} milestones reached
            </span>
          </p>
          {c.today ? (
            <ul className="grid gap-1 text-sm">
              {c.today.mood && <li>Mood: {EY_MOOD[c.today.mood] ?? c.today.mood}</li>}
              {c.today.arrival && <li>Arrived at {c.today.arrival.slice(0, 5)}</li>}
              {Object.entries(c.today.meals).map(([m, v]) => (
                <li key={m}>
                  {m}: {EY_MEAL[v] ?? v}
                </li>
              ))}
              {c.today.napFrom && (
                <li>
                  Nap {c.today.napFrom.slice(0, 5)}
                  {c.today.napTo ? `–${c.today.napTo.slice(0, 5)}` : ""}
                </li>
              )}
              {c.today.activities && <li>Activities: {c.today.activities}</li>}
              {c.today.note && <li className="font-semibold">Teacher: {c.today.note}</li>}
            </ul>
          ) : (
            <p className="sb-muted text-sm">Today&apos;s diary is not filled in yet.</p>
          )}
          {c.observations.length > 0 && (
            <div className="grid gap-2">
              <p className="text-sm font-semibold">Recent moments</p>
              {c.observations.map((o) => (
                <div key={o.id} className="flex gap-3 text-sm">
                  {o.photoUrl && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={o.photoUrl}
                      alt={`A moment on ${dateLong(o.observedOn)}`}
                      className="size-20 shrink-0 rounded-xl object-cover"
                    />
                  )}
                  <p>
                    <span className="sb-muted">{dateLong(o.observedOn)}: </span>
                    {o.note}
                  </p>
                </div>
              ))}
            </div>
          )}
          {c.pickups.length > 0 && (
            <p className="sb-muted text-xs">
              Also allowed to collect:{" "}
              {c.pickups.map((p) => `${p.name}${p.relation ? ` (${p.relation})` : ""}`).join(", ")}
            </p>
          )}
        </div>
      ))}
    </section>
  );
}

function PreferencesCard({ prefs, onSaved }: { prefs: Prefs; onSaved: (p: Prefs) => void }) {
  const [p, setP] = useState(prefs);
  const [msg, setMsg] = useState<string | null>(null);
  async function save() {
    const r = await call<Prefs>("preferences", "PUT", {
      whatsapp: p.whatsapp,
      sms: p.sms,
      email: p.email,
      language: p.language,
    });
    if (r.ok && r.data) {
      onSaved(r.data);
      setP(r.data);
      setMsg("Saved.");
    } else setMsg(r.message ?? "Could not save.");
  }
  return (
    <section aria-labelledby="pref" className="sb-card p-5">
      <h3 id="pref" className="text-lg font-bold">
        How the school contacts you
      </h3>
      <div className="mt-3 grid gap-2">
        {(
          [
            ["whatsapp", "WhatsApp messages"],
            ["sms", "SMS"],
            ["email", p.hasEmail ? "Email" : "Email (the school has no email address for you)"],
          ] as const
        ).map(([k, label]) => (
          <label key={k} className="flex min-h-11 cursor-pointer items-center gap-3">
            <input
              type="checkbox"
              className="size-5 accent-[var(--sb-color-primary)]"
              disabled={k === "email" && !p.hasEmail}
              checked={p[k]}
              onChange={(e) => setP({ ...p, [k]: e.target.checked })}
            />
            {label}
          </label>
        ))}
        <label className="mt-1 block max-w-xs">
          <span className="mb-1.5 block text-sm font-semibold">Preferred language</span>
          <select
            className="sb-input"
            value={p.language}
            onChange={(e) => setP({ ...p, language: e.target.value })}
          >
            <option value="en">English</option>
            <option value="hi">हिन्दी (Hindi)</option>
            <option value="te">తెలుగు (Telugu)</option>
            <option value="ta">தமிழ் (Tamil)</option>
            <option value="kn">ಕನ್ನಡ (Kannada)</option>
            <option value="mr">मराठी (Marathi)</option>
          </select>
        </label>
      </div>
      <div className="mt-4 flex items-center gap-3">
        <button
          type="button"
          className="sb-btn sb-btn-primary sb-focus"
          onClick={() => void save()}
        >
          Save preferences
        </button>
        {msg && (
          <span role="status" className="text-sm">
            {msg}
          </span>
        )}
      </div>
    </section>
  );
}
