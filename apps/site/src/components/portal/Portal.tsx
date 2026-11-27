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
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const r = await call<Me>("me");
    if (!r.ok) {
      if (r.status === 401) setMe("signed-out");
      else setError(r.message ?? null);
      return;
    }
    setMe(r.data!);
    const [c, q, p, pr] = await Promise.all([
      call<Circular[]>("circulars"),
      call<Req[]>("requests"),
      call<Ptm[]>("ptm"),
      call<Prefs>("preferences"),
    ]);
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
