"use client";

import { useCallback, useEffect, useState } from "react";
import { Camera, Check, LogOut, Phone, Plus, Send, Trash2, UserPlus } from "lucide-react";
import { sb, StudioError } from "@/lib/studio";
import {
  Badge,
  Btn,
  Card,
  Empty,
  Field,
  Loading,
  PageHeader,
  inputCls,
  useAction,
} from "@/components/kit";

type Entry = {
  id: string;
  kind: "WALK_IN" | "CALL_IN" | "CALL_OUT";
  name: string;
  phone?: string | null;
  purpose: string;
  meetWith?: string | null;
  peopleCount: number;
  notes?: string | null;
  inAt: string;
  outAt?: string | null;
  leadId?: string | null;
  createdByName?: string | null;
};
type Appointment = {
  id: string;
  visitorName: string;
  phone?: string | null;
  staffId?: string | null;
  staffName?: string | null;
  meetWith?: string | null;
  purpose?: string | null;
  startsAt: string;
  minutes: number;
  status: string;
  notes?: string | null;
};
type Post = {
  id: string;
  direction: "INWARD" | "OUTWARD";
  counterparty: string;
  addressee?: string | null;
  carrier?: string | null;
  trackingNo?: string | null;
  description?: string | null;
  loggedOn: string;
  status: string;
  handedTo?: string | null;
  closedAt?: string | null;
};
type Lost = {
  id: string;
  description: string;
  foundAt?: string | null;
  foundOn: string;
  hasPhoto: boolean;
  status: string;
  returnedTo?: string | null;
};
type Today = {
  walkIns: number;
  calls: number;
  inside: number;
  appointments: number;
  postToHandOver: number;
  postToDispatch: number;
  lostItems: number;
};
type Person = { id: string; name: string };
type View = "register" | "appointments" | "post" | "lost";

const KIND: Record<Entry["kind"], string> = {
  WALK_IN: "Walk-in",
  CALL_IN: "Call in",
  CALL_OUT: "Call out",
};
const PURPOSE: Record<string, string> = {
  ADMISSION: "Admission",
  FEES: "Fees",
  MEET_STAFF: "Meet staff",
  DOCUMENTS: "Documents / TC",
  COMPLAINT: "Complaint",
  DELIVERY: "Delivery",
  OTHER: "Other",
};
const APPT_TONE: Record<string, "blue" | "purple" | "green" | "gray" | "red"> = {
  SCHEDULED: "blue",
  ARRIVED: "purple",
  DONE: "green",
  CANCELLED: "gray",
  NO_SHOW: "red",
};
const APPT_LABEL: Record<string, string> = {
  SCHEDULED: "Scheduled",
  ARRIVED: "Arrived",
  DONE: "Done",
  CANCELLED: "Cancelled",
  NO_SHOW: "No-show",
};

const ist = (opts: Intl.DateTimeFormatOptions) =>
  new Intl.DateTimeFormat("en-IN", { timeZone: "Asia/Kolkata", ...opts });
const time = (iso: string) => ist({ hour: "numeric", minute: "2-digit" }).format(new Date(iso));
const dayLabel = (iso: string) =>
  ist({ weekday: "short", day: "numeric", month: "short" }).format(new Date(iso));
const dayKey = (iso: string) =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date(iso));
const todayKey = () => dayKey(new Date().toISOString());
const fmtDay = (d: string) =>
  ist({ day: "numeric", month: "short" }).format(new Date(`${d}T00:00:00+05:30`));

export function FrontOfficePage({ hasAdmissions }: { hasAdmissions: boolean }) {
  const [view, setView] = useState<View>("register");
  const [today, setToday] = useState<Today | null>(null);
  const [blocked, setBlocked] = useState<string | null>(null);

  const loadToday = useCallback(async () => {
    try {
      setToday(await sb<Today>("front-office/summary"));
    } catch (e) {
      if (e instanceof StudioError && e.status === 403) setBlocked(e.message);
    }
  }, []);
  useEffect(() => {
    const t = setTimeout(() => void loadToday(), 0);
    return () => clearTimeout(t);
  }, [loadToday]);

  if (blocked) {
    return (
      <div>
        <PageHeader title="Front office" />
        <Card className="max-w-2xl">
          <h2 className="text-lg font-semibold">Front office is not part of your plan yet</h2>
          <p className="text-ink-muted mt-2 text-sm">
            It is included in ShikshaBlox Standard: walk-in and call register, appointments, post
            and couriers, and lost & found.
          </p>
        </Card>
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="Front office"
        subtitle="Visitors, calls, appointments, post and lost & found."
      />
      {today && (
        <div className="mb-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {(
            [
              ["Walk-ins today", today.walkIns, `${today.inside} still inside`],
              ["Calls today", today.calls, ""],
              ["Appointments today", today.appointments, "still to come"],
              [
                "Post to hand over",
                today.postToHandOver,
                today.postToDispatch ? `${today.postToDispatch} to dispatch` : "",
              ],
            ] as [string, number, string][]
          ).map(([k, v, sub]) => (
            <div key={k} className="rounded-2xl bg-white p-4 ring-1 ring-black/5">
              <p className="text-2xl font-bold tabular-nums">{v}</p>
              <p className="text-ink-muted text-xs">{k}</p>
              {sub && <p className="text-ink-muted text-xs">{sub}</p>}
            </div>
          ))}
        </div>
      )}
      <div className="mb-4 flex flex-wrap gap-2">
        {(
          [
            ["register", "Register"],
            ["appointments", "Appointments"],
            ["post", "Post & couriers"],
            ["lost", `Lost & found${today?.lostItems ? ` (${today.lostItems})` : ""}`],
          ] as [View, string][]
        ).map(([v, label]) => (
          <button
            key={v}
            type="button"
            onClick={() => setView(v)}
            className={`rounded-full px-3.5 py-1.5 text-sm font-medium transition ${view === v ? "bg-primary text-on-primary" : "text-ink-muted hover:text-ink bg-white ring-1 ring-black/10"}`}
          >
            {label}
          </button>
        ))}
      </div>
      {view === "register" ? (
        <Register hasAdmissions={hasAdmissions} onChanged={() => void loadToday()} />
      ) : view === "appointments" ? (
        <Appointments onChanged={() => void loadToday()} />
      ) : view === "post" ? (
        <PostView onChanged={() => void loadToday()} />
      ) : (
        <LostFound onChanged={() => void loadToday()} />
      )}
    </div>
  );
}

// ------------------------------------------------------------------ register

function Register({ hasAdmissions, onChanged }: { hasAdmissions: boolean; onChanged: () => void }) {
  const [day, setDay] = useState(todayKey());
  const [rows, setRows] = useState<Entry[] | null>(null);
  const empty = {
    kind: "WALK_IN",
    name: "",
    phone: "",
    purpose: "MEET_STAFF",
    meetWith: "",
    peopleCount: "1",
    notes: "",
  };
  const [f, setF] = useState(empty);
  const [handing, setHanding] = useState<string | null>(null);
  const [h, setH] = useState({ studentName: "", gradeSought: "" });
  const { busy, run } = useAction();

  const load = useCallback(
    async () => setRows(await sb<Entry[]>(`front-office/entries?day=${day}`)),
    [day],
  );
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  const set = (k: keyof typeof f) => (e: { target: { value: string } }) =>
    setF({ ...f, [k]: e.target.value });

  async function add(e: React.FormEvent) {
    e.preventDefault();
    const r = await run(
      "add",
      () =>
        sb<Entry>("front-office/entries", {
          body: {
            ...f,
            phone: f.phone || null,
            meetWith: f.meetWith || null,
            notes: f.notes || null,
            peopleCount: Number(f.peopleCount),
          },
        }),
      "Logged",
    );
    if (r) {
      setF({ ...empty, kind: f.kind });
      void load();
      onChanged();
    }
  }
  async function out(id: string) {
    if (
      await run(
        "out" + id,
        () => sb<Entry>(`front-office/entries/${id}/out`, { method: "POST" }),
        "Marked as left",
      )
    ) {
      void load();
      onChanged();
    }
  }
  async function handOff(id: string) {
    const r = await run(
      "hand" + id,
      () => sb<Entry>(`front-office/entries/${id}/admissions`, { body: h }),
      "Added to admissions",
    );
    if (r) {
      setHanding(null);
      setH({ studentName: "", gradeSought: "" });
      void load();
    }
  }

  return (
    <div>
      {day === todayKey() && (
        <Card className="mb-5">
          <form onSubmit={(e) => void add(e)}>
            <div className="mb-3 flex flex-wrap gap-1.5" role="radiogroup" aria-label="Type">
              {(Object.keys(KIND) as Entry["kind"][]).map((k) => (
                <Btn
                  key={k}
                  size="sm"
                  type="button"
                  variant={f.kind === k ? "primary" : "secondary"}
                  onClick={() => setF({ ...f, kind: k })}
                  aria-pressed={f.kind === k}
                >
                  {k === "WALK_IN" ? (
                    <UserPlus className="size-3.5" aria-hidden />
                  ) : (
                    <Phone className="size-3.5" aria-hidden />
                  )}{" "}
                  {KIND[k]}
                </Btn>
              ))}
            </div>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <Field label="Name *">
                <input
                  className={inputCls}
                  required
                  maxLength={120}
                  value={f.name}
                  onChange={set("name")}
                  autoComplete="off"
                />
              </Field>
              <Field label="Phone">
                <input
                  className={inputCls}
                  type="tel"
                  inputMode="tel"
                  maxLength={20}
                  value={f.phone}
                  onChange={set("phone")}
                />
              </Field>
              <Field label="Purpose">
                <select className={inputCls} value={f.purpose} onChange={set("purpose")}>
                  {Object.entries(PURPOSE).map(([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="To meet">
                <input
                  className={inputCls}
                  maxLength={120}
                  value={f.meetWith}
                  onChange={set("meetWith")}
                  placeholder="e.g. Principal, Accounts"
                />
              </Field>
              {f.kind === "WALK_IN" && (
                <Field label="People">
                  <input
                    className={inputCls}
                    type="number"
                    min={1}
                    max={50}
                    value={f.peopleCount}
                    onChange={set("peopleCount")}
                  />
                </Field>
              )}
              <div
                className={
                  f.kind === "WALK_IN"
                    ? "sm:col-span-1 lg:col-span-3"
                    : "sm:col-span-2 lg:col-span-4"
                }
              >
                <Field label="Notes">
                  <input
                    className={inputCls}
                    maxLength={1000}
                    value={f.notes}
                    onChange={set("notes")}
                  />
                </Field>
              </div>
            </div>
            <Btn type="submit" variant="primary" className="mt-3" busy={busy === "add"}>
              <Plus className="size-4" aria-hidden /> Log{" "}
              {KIND[f.kind as Entry["kind"]].toLowerCase()}
            </Btn>
          </form>
        </Card>
      )}
      <div className="mb-3 flex items-center gap-2">
        <label className="text-sm" htmlFor="reg-day">
          Day
        </label>
        <input
          id="reg-day"
          className={`${inputCls} w-auto!`}
          type="date"
          max={todayKey()}
          value={day}
          onChange={(e) => setDay(e.target.value || todayKey())}
        />
      </div>
      {!rows ? (
        <Loading />
      ) : rows.length === 0 ? (
        <Empty>No visitors or calls logged for this day.</Empty>
      ) : (
        <ul className="overflow-hidden rounded-2xl bg-white ring-1 ring-black/5">
          {rows.map((r) => (
            <li key={r.id} className="border-b border-black/5 px-4 py-3 last:border-0">
              <div className="flex flex-wrap items-start gap-x-3 gap-y-1">
                <span className="text-ink-muted w-16 shrink-0 pt-0.5 text-sm tabular-nums">
                  {time(r.inAt)}
                </span>
                <div className="min-w-[11rem] flex-1">
                  <p className="text-sm font-semibold">
                    {r.name}
                    {r.peopleCount > 1 && (
                      <span className="text-ink-muted font-normal"> +{r.peopleCount - 1}</span>
                    )}
                  </p>
                  <p className="text-ink-muted text-xs">
                    {[
                      KIND[r.kind],
                      PURPOSE[r.purpose],
                      r.meetWith && `to meet ${r.meetWith}`,
                      r.phone,
                    ]
                      .filter(Boolean)
                      .join(" · ")}
                  </p>
                  {r.notes && <p className="text-ink-muted mt-0.5 text-sm">{r.notes}</p>}
                </div>
                <div className="flex flex-wrap items-center gap-1.5">
                  {r.kind === "WALK_IN" &&
                    (r.outAt ? (
                      <span className="text-ink-muted text-xs">Left {time(r.outAt)}</span>
                    ) : (
                      <Btn size="sm" busy={busy === "out" + r.id} onClick={() => void out(r.id)}>
                        <LogOut className="size-3.5" aria-hidden /> Left
                      </Btn>
                    ))}
                  <DeleteBtn
                    path={`front-office/entries/${r.id}`}
                    label={`the entry for ${r.name}`}
                    onDone={() => {
                      void load();
                      onChanged();
                    }}
                  />
                  {r.leadId ? (
                    <Badge tone="green">In admissions</Badge>
                  ) : (
                    hasAdmissions &&
                    r.purpose === "ADMISSION" && (
                      <Btn
                        size="sm"
                        variant="primary"
                        onClick={() => setHanding(handing === r.id ? null : r.id)}
                      >
                        <Send className="size-3.5" aria-hidden /> To admissions
                      </Btn>
                    )
                  )}
                </div>
              </div>
              {handing === r.id && (
                <div className="mt-2 flex flex-wrap items-end gap-2 rounded-xl bg-black/[0.03] p-3 sm:ml-16">
                  <Field label="Student name">
                    <input
                      className={inputCls}
                      maxLength={120}
                      value={h.studentName}
                      onChange={(e) => setH({ ...h, studentName: e.target.value })}
                    />
                  </Field>
                  <Field label="Class sought">
                    <input
                      className={inputCls}
                      maxLength={60}
                      placeholder="e.g. Class 4"
                      value={h.gradeSought}
                      onChange={(e) => setH({ ...h, gradeSought: e.target.value })}
                    />
                  </Field>
                  <Btn
                    variant="primary"
                    busy={busy === "hand" + r.id}
                    onClick={() => void handOff(r.id)}
                  >
                    Create lead
                  </Btn>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// ------------------------------------------------------------------ appointments

function Appointments({ onChanged }: { onChanged: () => void }) {
  const [rows, setRows] = useState<Appointment[] | null>(null);
  const [staff, setStaff] = useState<Person[]>([]);
  const [adding, setAdding] = useState(false);
  const [f, setF] = useState({
    visitorName: "",
    phone: "",
    staffId: "",
    meetWith: "",
    purpose: "",
    date: todayKey(),
    time: "10:00",
    minutes: "15",
  });
  const { busy, run } = useAction();

  const load = useCallback(
    async () => setRows(await sb<Appointment[]>("front-office/appointments")),
    [],
  );
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    sb<Person[]>("front-office/staff")
      .then(setStaff)
      .catch(() => {});
    return () => clearTimeout(t);
  }, [load]);

  const set = (k: keyof typeof f) => (e: { target: { value: string } }) =>
    setF({ ...f, [k]: e.target.value });

  async function book(e: React.FormEvent) {
    e.preventDefault();
    const r = await run(
      "book",
      () =>
        sb<Appointment>("front-office/appointments", {
          body: {
            visitorName: f.visitorName,
            phone: f.phone || null,
            staffId: f.staffId || null,
            meetWith: f.staffId ? null : f.meetWith || null,
            purpose: f.purpose || null,
            startsAt: new Date(`${f.date}T${f.time}:00+05:30`).toISOString(),
            minutes: Number(f.minutes),
          },
        }),
      "Appointment booked",
    );
    if (r) {
      setAdding(false);
      setF({ ...f, visitorName: "", phone: "", purpose: "" });
      void load();
      onChanged();
    }
  }
  async function status(id: string, s: string) {
    if (
      await run(
        "st" + id,
        () => sb<Appointment>(`front-office/appointments/${id}/status`, { body: { status: s } }),
        APPT_LABEL[s],
      )
    ) {
      void load();
      onChanged();
    }
  }

  const days: [string, Appointment[]][] = [];
  for (const a of rows ?? []) {
    const k = dayKey(a.startsAt);
    const last = days[days.length - 1];
    if (last && last[0] === k) last[1].push(a);
    else days.push([k, [a]]);
  }

  return (
    <div>
      {adding ? (
        <Card className="mb-5">
          <form onSubmit={(e) => void book(e)}>
            <h2 className="text-lg font-semibold">Book an appointment</h2>
            <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <Field label="Visitor *">
                <input
                  className={inputCls}
                  required
                  maxLength={120}
                  value={f.visitorName}
                  onChange={set("visitorName")}
                />
              </Field>
              <Field label="Phone">
                <input
                  className={inputCls}
                  type="tel"
                  maxLength={20}
                  value={f.phone}
                  onChange={set("phone")}
                />
              </Field>
              <Field label="With (staff)">
                <select className={inputCls} value={f.staffId} onChange={set("staffId")}>
                  <option value="">Someone else…</option>
                  {staff.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </select>
              </Field>
              {!f.staffId && (
                <Field label="Meeting with *">
                  <input
                    className={inputCls}
                    required
                    maxLength={120}
                    value={f.meetWith}
                    onChange={set("meetWith")}
                    placeholder="e.g. Principal"
                  />
                </Field>
              )}
              <Field label="Date">
                <input
                  className={inputCls}
                  type="date"
                  required
                  min={todayKey()}
                  value={f.date}
                  onChange={set("date")}
                />
              </Field>
              <Field label="Time">
                <input
                  className={inputCls}
                  type="time"
                  required
                  value={f.time}
                  onChange={set("time")}
                />
              </Field>
              <Field label="Minutes">
                <select className={inputCls} value={f.minutes} onChange={set("minutes")}>
                  {[10, 15, 20, 30, 45, 60].map((m) => (
                    <option key={m}>{m}</option>
                  ))}
                </select>
              </Field>
              <Field label="Purpose">
                <input
                  className={inputCls}
                  maxLength={300}
                  value={f.purpose}
                  onChange={set("purpose")}
                />
              </Field>
            </div>
            <div className="mt-4 flex gap-2">
              <Btn type="submit" variant="primary" busy={busy === "book"}>
                Book
              </Btn>
              <Btn variant="ghost" onClick={() => setAdding(false)}>
                Cancel
              </Btn>
            </div>
          </form>
        </Card>
      ) : (
        <Btn variant="primary" className="mb-4" onClick={() => setAdding(true)}>
          <Plus className="size-4" aria-hidden /> Book appointment
        </Btn>
      )}
      {!rows ? (
        <Loading />
      ) : rows.length === 0 ? (
        <Empty>No appointments in the next 7 days.</Empty>
      ) : (
        <div className="space-y-5">
          {days.map(([k, list]) => (
            <section key={k}>
              <h2 className="text-ink-muted mb-2 text-xs font-semibold tracking-wide uppercase">
                {k === todayKey() ? "Today" : dayLabel(list[0]!.startsAt)}
              </h2>
              <ul className="overflow-hidden rounded-2xl bg-white ring-1 ring-black/5">
                {list.map((a) => (
                  <li
                    key={a.id}
                    className="flex flex-wrap items-center gap-x-3 gap-y-2 border-b border-black/5 px-4 py-3 last:border-0"
                  >
                    <span className="w-16 shrink-0 text-sm font-semibold tabular-nums">
                      {time(a.startsAt)}
                    </span>
                    <div className="min-w-[11rem] flex-1">
                      <p className="text-sm font-semibold">{a.visitorName}</p>
                      <p className="text-ink-muted text-xs">
                        {[
                          `with ${a.staffName ?? a.meetWith}`,
                          `${a.minutes} min`,
                          a.purpose,
                          a.phone,
                        ]
                          .filter(Boolean)
                          .join(" · ")}
                      </p>
                    </div>
                    <Badge tone={APPT_TONE[a.status]}>{APPT_LABEL[a.status]}</Badge>
                    <DeleteBtn
                      path={`front-office/appointments/${a.id}`}
                      label={`the appointment with ${a.visitorName}`}
                      onDone={() => {
                        void load();
                        onChanged();
                      }}
                    />
                    {a.status === "SCHEDULED" && (
                      <span className="flex gap-1">
                        <Btn
                          size="sm"
                          variant="primary"
                          busy={busy === "st" + a.id}
                          onClick={() => void status(a.id, "ARRIVED")}
                        >
                          Arrived
                        </Btn>
                        <Btn size="sm" variant="ghost" onClick={() => void status(a.id, "NO_SHOW")}>
                          No-show
                        </Btn>
                        <Btn
                          size="sm"
                          variant="ghost"
                          onClick={() => void status(a.id, "CANCELLED")}
                        >
                          Cancel
                        </Btn>
                      </span>
                    )}
                    {a.status === "ARRIVED" && (
                      <Btn
                        size="sm"
                        busy={busy === "st" + a.id}
                        onClick={() => void status(a.id, "DONE")}
                      >
                        <Check className="size-3.5" aria-hidden /> Done
                      </Btn>
                    )}
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      )}
    </div>
  );
}

// ------------------------------------------------------------------ post

function PostView({ onChanged }: { onChanged: () => void }) {
  const [rows, setRows] = useState<Post[] | null>(null);
  const [openOnly, setOpenOnly] = useState(true);
  const [adding, setAdding] = useState<"INWARD" | "OUTWARD" | null>(null);
  const [f, setF] = useState({
    counterparty: "",
    addressee: "",
    carrier: "",
    trackingNo: "",
    description: "",
  });
  const [closing, setClosing] = useState<string | null>(null);
  const [person, setPerson] = useState("");
  const [tracking, setTracking] = useState("");
  const { busy, run } = useAction();

  const load = useCallback(
    async () => setRows(await sb<Post[]>(`front-office/post?open=${openOnly}`)),
    [openOnly],
  );
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  const set = (k: keyof typeof f) => (e: { target: { value: string } }) =>
    setF({ ...f, [k]: e.target.value });

  async function log(e: React.FormEvent) {
    e.preventDefault();
    const r = await run(
      "log",
      () => sb<Post>("front-office/post", { body: { ...f, direction: adding } }),
      "Logged",
    );
    if (r) {
      setAdding(null);
      setF({ counterparty: "", addressee: "", carrier: "", trackingNo: "", description: "" });
      void load();
      onChanged();
    }
  }
  async function close(p: Post) {
    const r = await run(
      "close" + p.id,
      () =>
        sb<Post>(`front-office/post/${p.id}/close`, {
          body: { person, trackingNo: tracking || null },
        }),
      p.direction === "INWARD" ? "Handed over" : "Dispatched",
    );
    if (r) {
      setClosing(null);
      setPerson("");
      setTracking("");
      void load();
      onChanged();
    }
  }

  const inward = adding === "INWARD";
  return (
    <div>
      {adding ? (
        <Card className="mb-5">
          <form onSubmit={(e) => void log(e)}>
            <h2 className="text-lg font-semibold">
              {inward ? "Received post / courier" : "Post / courier to send"}
            </h2>
            <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <Field label={inward ? "From (sender) *" : "To (recipient) *"}>
                <input
                  className={inputCls}
                  required
                  maxLength={160}
                  value={f.counterparty}
                  onChange={set("counterparty")}
                />
              </Field>
              <Field label={inward ? "For (staff / office)" : "From (staff / office)"}>
                <input
                  className={inputCls}
                  maxLength={120}
                  value={f.addressee}
                  onChange={set("addressee")}
                />
              </Field>
              <Field label="Carrier">
                <input
                  className={inputCls}
                  maxLength={80}
                  value={f.carrier}
                  onChange={set("carrier")}
                  placeholder="e.g. Speed Post, DTDC"
                />
              </Field>
              <Field label="Tracking no.">
                <input
                  className={inputCls}
                  maxLength={80}
                  value={f.trackingNo}
                  onChange={set("trackingNo")}
                />
              </Field>
              <div className="sm:col-span-2">
                <Field label="Description">
                  <input
                    className={inputCls}
                    maxLength={300}
                    value={f.description}
                    onChange={set("description")}
                  />
                </Field>
              </div>
            </div>
            <div className="mt-4 flex gap-2">
              <Btn type="submit" variant="primary" busy={busy === "log"}>
                Log
              </Btn>
              <Btn variant="ghost" onClick={() => setAdding(null)}>
                Cancel
              </Btn>
            </div>
          </form>
        </Card>
      ) : (
        <div className="mb-4 flex flex-wrap gap-2">
          <Btn variant="primary" onClick={() => setAdding("INWARD")}>
            <Plus className="size-4" aria-hidden /> Received
          </Btn>
          <Btn onClick={() => setAdding("OUTWARD")}>
            <Plus className="size-4" aria-hidden /> To send
          </Btn>
          <label className="text-ink-muted ml-auto flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              className="size-4"
              checked={openOnly}
              onChange={(e) => setOpenOnly(e.target.checked)}
            />{" "}
            Pending only
          </label>
        </div>
      )}
      {!rows ? (
        <Loading />
      ) : rows.length === 0 ? (
        <Empty>
          {openOnly ? "Nothing waiting to be handed over or dispatched." : "No post logged yet."}
        </Empty>
      ) : (
        <ul className="overflow-hidden rounded-2xl bg-white ring-1 ring-black/5">
          {rows.map((p) => {
            const open = p.status === "RECEIVED" || p.status === "READY";
            return (
              <li key={p.id} className="border-b border-black/5 px-4 py-3 last:border-0">
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                  <Badge tone={p.direction === "INWARD" ? "blue" : "purple"}>
                    {p.direction === "INWARD" ? "In" : "Out"}
                  </Badge>
                  <div className="min-w-[11rem] flex-1">
                    <p className="text-sm font-semibold">
                      {p.direction === "INWARD" ? `From ${p.counterparty}` : `To ${p.counterparty}`}
                      {p.addressee && (
                        <span className="text-ink-muted font-normal">
                          {" "}
                          · {p.direction === "INWARD" ? "for" : "from"} {p.addressee}
                        </span>
                      )}
                    </p>
                    <p className="text-ink-muted text-xs">
                      {[fmtDay(p.loggedOn), p.carrier, p.trackingNo, p.description]
                        .filter(Boolean)
                        .join(" · ")}
                    </p>
                  </div>
                  {open ? (
                    <Btn size="sm" onClick={() => setClosing(closing === p.id ? null : p.id)}>
                      {p.direction === "INWARD" ? "Hand over…" : "Dispatched…"}
                    </Btn>
                  ) : (
                    <span className="text-ink-muted text-xs">
                      {p.direction === "INWARD"
                        ? `Given to ${p.handedTo}`
                        : `Sent${p.handedTo ? ` by ${p.handedTo}` : ""}`}
                    </span>
                  )}
                  <DeleteBtn
                    path={`front-office/post/${p.id}`}
                    label="this post entry"
                    onDone={() => {
                      void load();
                      onChanged();
                    }}
                  />
                </div>
                {closing === p.id && (
                  <div className="mt-2 flex flex-wrap items-end gap-2 rounded-xl bg-black/[0.03] p-3">
                    <Field label={p.direction === "INWARD" ? "Handed to *" : "Sent by"}>
                      <input
                        className={inputCls}
                        maxLength={120}
                        value={person}
                        onChange={(e) => setPerson(e.target.value)}
                      />
                    </Field>
                    {p.direction === "OUTWARD" && (
                      <Field label="Tracking no.">
                        <input
                          className={inputCls}
                          maxLength={80}
                          value={tracking}
                          onChange={(e) => setTracking(e.target.value)}
                        />
                      </Field>
                    )}
                    <Btn
                      variant="primary"
                      busy={busy === "close" + p.id}
                      disabled={p.direction === "INWARD" && !person.trim()}
                      onClick={() => void close(p)}
                    >
                      Save
                    </Btn>
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

// ------------------------------------------------------------------ lost & found

function LostFound({ onChanged }: { onChanged: () => void }) {
  const [rows, setRows] = useState<Lost[] | null>(null);
  const [photos, setPhotos] = useState<Record<string, string>>({});
  const [adding, setAdding] = useState(false);
  const [f, setF] = useState({ description: "", foundAt: "" });
  const [photo, setPhoto] = useState<File | null>(null);
  const [returning, setReturning] = useState<string | null>(null);
  const [to, setTo] = useState("");
  const { busy, run } = useAction();

  const load = useCallback(async () => {
    const list = await sb<Lost[]>("front-office/lost-found");
    setRows(list);
    const urls: Record<string, string> = {};
    await Promise.all(
      list
        .filter((l) => l.hasPhoto && l.status === "FOUND")
        .map(async (l) => {
          try {
            urls[l.id] = (await sb<{ url: string }>(`front-office/lost-found/${l.id}/photo`)).url;
          } catch {
            /* photo link is optional */
          }
        }),
    );
    setPhotos(urls);
  }, []);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  async function add(e: React.FormEvent) {
    e.preventDefault();
    const form = new FormData();
    form.set("description", f.description);
    if (f.foundAt) form.set("foundAt", f.foundAt);
    if (photo) form.set("photo", photo);
    const r = await run("add", () => sb<Lost>("front-office/lost-found", { form }), "Logged");
    if (r) {
      setAdding(false);
      setF({ description: "", foundAt: "" });
      setPhoto(null);
      void load();
      onChanged();
    }
  }
  async function setStatus(id: string, status: string) {
    const r = await run(
      "st" + id,
      () => sb<Lost>(`front-office/lost-found/${id}/status`, { body: { status, returnedTo: to } }),
      status === "RETURNED" ? "Returned" : "Updated",
    );
    if (r) {
      setReturning(null);
      setTo("");
      void load();
      onChanged();
    }
  }

  return (
    <div>
      {adding ? (
        <Card className="mb-5">
          <form onSubmit={(e) => void add(e)}>
            <h2 className="text-lg font-semibold">Log a found item</h2>
            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              <Field label="What is it? *">
                <input
                  className={inputCls}
                  required
                  maxLength={300}
                  value={f.description}
                  onChange={(e) => setF({ ...f, description: e.target.value })}
                  placeholder="e.g. Red lunch box with Spider-Man sticker"
                />
              </Field>
              <Field label="Found at">
                <input
                  className={inputCls}
                  maxLength={120}
                  value={f.foundAt}
                  onChange={(e) => setF({ ...f, foundAt: e.target.value })}
                  placeholder="e.g. Playground"
                />
              </Field>
            </div>
            <label className="mt-3 inline-flex h-10 cursor-pointer items-center gap-1.5 rounded-full bg-white px-4 text-sm font-medium ring-1 ring-black/10">
              <Camera className="size-4" aria-hidden /> {photo ? photo.name : "Add photo"}
              <input
                type="file"
                accept="image/jpeg,image/png"
                capture="environment"
                className="sr-only"
                onChange={(e) => setPhoto(e.target.files?.[0] ?? null)}
              />
            </label>
            <div className="mt-4 flex gap-2">
              <Btn type="submit" variant="primary" busy={busy === "add"}>
                Log item
              </Btn>
              <Btn variant="ghost" onClick={() => setAdding(false)}>
                Cancel
              </Btn>
            </div>
          </form>
        </Card>
      ) : (
        <Btn variant="primary" className="mb-4" onClick={() => setAdding(true)}>
          <Plus className="size-4" aria-hidden /> Found item
        </Btn>
      )}
      {!rows ? (
        <Loading />
      ) : rows.length === 0 ? (
        <Empty>No lost & found items.</Empty>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {rows.map((l) => (
            <li
              key={l.id}
              className={`rounded-2xl bg-white p-3 ring-1 ring-black/5 ${l.status !== "FOUND" ? "opacity-70" : ""}`}
            >
              {photos[l.id] && (
                // eslint-disable-next-line @next/next/no-img-element -- short-lived signed URL, not optimisable
                <img
                  src={photos[l.id]}
                  alt={l.description}
                  className="mb-2 aspect-[4/3] w-full rounded-xl object-cover"
                />
              )}
              <p className="text-sm font-semibold">{l.description}</p>
              <p className="text-ink-muted text-xs">
                {[fmtDay(l.foundOn), l.foundAt].filter(Boolean).join(" · ")}
              </p>
              {l.status === "FOUND" ? (
                returning === l.id ? (
                  <div className="mt-2 flex gap-2">
                    <input
                      className={inputCls}
                      placeholder="Returned to (name, class)"
                      value={to}
                      onChange={(e) => setTo(e.target.value)}
                      aria-label="Returned to"
                    />
                    <Btn
                      variant="primary"
                      disabled={!to.trim()}
                      busy={busy === "st" + l.id}
                      onClick={() => void setStatus(l.id, "RETURNED")}
                    >
                      Save
                    </Btn>
                  </div>
                ) : (
                  <div className="mt-2 flex gap-1.5">
                    <Btn size="sm" variant="primary" onClick={() => setReturning(l.id)}>
                      Returned…
                    </Btn>
                    <Btn
                      size="sm"
                      variant="ghost"
                      busy={busy === "st" + l.id}
                      onClick={() => void setStatus(l.id, "DISPOSED")}
                    >
                      Disposed
                    </Btn>
                  </div>
                )
              ) : (
                <p className="mt-2 text-xs">
                  {l.status === "RETURNED" ? (
                    <Badge tone="green">Returned to {l.returnedTo}</Badge>
                  ) : (
                    <Badge>Disposed</Badge>
                  )}
                </p>
              )}
              <div className="mt-1 text-right">
                <DeleteBtn
                  path={`front-office/lost-found/${l.id}`}
                  label={`“${l.description}”`}
                  onDone={() => {
                    void load();
                    onChanged();
                  }}
                />
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** Small "delete this mistaken record" button with a confirm. */
function DeleteBtn({ path, label, onDone }: { path: string; label: string; onDone: () => void }) {
  const { busy, run } = useAction();
  return (
    <Btn
      size="sm"
      variant="ghost"
      busy={busy === "del"}
      aria-label={`Delete ${label}`}
      onClick={async () => {
        if (!confirm(`Delete ${label}? This cannot be undone.`)) return;
        if (await run("del", () => sb(path, { method: "DELETE" }).then(() => true), "Deleted"))
          onDone();
      }}
    >
      <Trash2 className="size-3.5" aria-hidden />
    </Btn>
  );
}
