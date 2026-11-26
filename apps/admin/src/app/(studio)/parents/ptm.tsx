"use client";

import { useCallback, useEffect, useState } from "react";
import { Plus, Printer, Trash2, X } from "lucide-react";
import { sb } from "@/lib/studio";
import { Badge, Btn, Card, Empty, Field, Loading, inputCls, useAction } from "@/components/kit";

type EventSummary = {
  id: string;
  title: string;
  eventDate: string;
  startTime: string;
  endTime: string;
  slotMinutes: number;
  instructions?: string | null;
  bookingOpen: boolean;
  hosts: number;
  slots: number;
  booked: number;
};
type Booking = {
  id: string;
  slotStart: string;
  parentName: string;
  phone: string;
  studentName: string;
  studentClass?: string | null;
  code: string;
  status: string;
};
type HostSchedule = {
  id: string;
  name: string;
  label?: string | null;
  room?: string | null;
  slots: { start: string; booking?: Booking | null }[];
};
type Schedule = { event: EventSummary; hosts: HostSchedule[] };

const today = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date());
const fmtDay = (d: string) =>
  new Intl.DateTimeFormat("en-IN", {
    weekday: "short",
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(new Date(`${d}T00:00:00`));
const fmtTime = (t: string) => {
  const [h, m] = t.split(":").map(Number);
  return new Intl.DateTimeFormat("en-IN", { hour: "numeric", minute: "2-digit" }).format(
    new Date(2000, 0, 1, h, m),
  );
};
const BOOKING_TONE: Record<string, "blue" | "green" | "red" | "gray"> = {
  BOOKED: "blue",
  ATTENDED: "green",
  NO_SHOW: "red",
  CANCELLED: "gray",
};

/** "Mrs Rao | Class 3A | Room 12" per line. */
function parseHosts(text: string) {
  return text
    .split("\n")
    .map((l) => l.trim())
    .filter(Boolean)
    .map((l) => {
      const [name, label, room] = l.split("|").map((x) => x.trim());
      return { name: name ?? "", label: label || null, room: room || null };
    });
}

export function PtmView({ siteUrl }: { siteUrl?: string }) {
  const [events, setEvents] = useState<EventSummary[] | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [sel, setSel] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);

  const load = useCallback(async () => {
    try {
      setEvents(await sb<EventSummary[]>("engagement/ptm"));
    } catch (e) {
      setErr((e as Error).message);
    }
  }, []);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  if (err) return <Empty>{err}</Empty>;
  if (sel)
    return (
      <ScheduleView
        id={sel}
        onBack={() => {
          setSel(null);
          void load();
        }}
      />
    );
  return (
    <div>
      {siteUrl && (
        <p className="text-ink-muted mb-4 text-sm">
          Parents book at{" "}
          <a
            className="text-primary font-medium underline"
            href={`${siteUrl}/ptm`}
            target="_blank"
            rel="noopener noreferrer"
          >
            {siteUrl.replace(/^https?:\/\//, "")}/ptm
          </a>{" "}
          while booking is open.
        </p>
      )}
      {adding ? (
        <NewEvent
          onClose={() => setAdding(false)}
          onCreated={(e) => {
            setAdding(false);
            void load();
            setSel(e.id);
          }}
        />
      ) : (
        <Btn variant="primary" className="mb-4" onClick={() => setAdding(true)}>
          <Plus className="size-4" aria-hidden /> New PTM
        </Btn>
      )}
      {!events ? (
        <Loading />
      ) : events.length === 0 ? (
        <Empty>
          No PTMs yet. Create one with the teachers who will meet parents; parents then pick a slot
          on your website.
        </Empty>
      ) : (
        <ul className="overflow-hidden rounded-2xl bg-white ring-1 ring-black/5">
          {events.map((e) => (
            <li key={e.id} className="border-b border-black/5 last:border-0">
              <button
                type="button"
                onClick={() => setSel(e.id)}
                className="flex w-full flex-wrap items-center gap-x-3 gap-y-1 px-4 py-3 text-left hover:bg-black/[0.02]"
              >
                <span className="min-w-[12rem] flex-1">
                  <span className="block text-sm font-semibold">{e.title}</span>
                  <span className="text-ink-muted block text-xs">
                    {fmtDay(e.eventDate)} · {fmtTime(e.startTime)}–{fmtTime(e.endTime)} · {e.hosts}{" "}
                    teachers
                  </span>
                </span>
                <span className="text-sm tabular-nums">
                  {e.booked}/{e.slots} booked
                </span>
                {e.eventDate < today() ? (
                  <Badge>Past</Badge>
                ) : e.bookingOpen ? (
                  <Badge tone="green">Booking open</Badge>
                ) : (
                  <Badge tone="amber">Booking closed</Badge>
                )}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function NewEvent({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: (e: EventSummary) => void;
}) {
  const [f, setF] = useState({
    title: "Parent-teacher meeting",
    eventDate: "",
    startTime: "09:00",
    endTime: "12:00",
    slotMinutes: "10",
    instructions: "",
    hosts: "",
  });
  const { busy, run } = useAction();
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) =>
    setF({ ...f, [k]: e.target.value });
  async function save(e: React.FormEvent) {
    e.preventDefault();
    const r = await run(
      "add",
      () =>
        sb<EventSummary>("engagement/ptm", {
          body: {
            ...f,
            slotMinutes: Number(f.slotMinutes),
            instructions: f.instructions || null,
            bookingOpen: true,
            hosts: parseHosts(f.hosts),
          },
        }),
      "PTM created",
    );
    if (r) onCreated(r);
  }
  return (
    <Card className="mb-5">
      <form onSubmit={(e) => void save(e)}>
        <h2 className="text-lg font-semibold">New PTM</h2>
        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          <div className="sm:col-span-2">
            <Field label="Title *">
              <input
                className={inputCls}
                required
                maxLength={160}
                value={f.title}
                onChange={set("title")}
              />
            </Field>
          </div>
          <Field label="Date *">
            <input
              className={inputCls}
              type="date"
              required
              min={today()}
              value={f.eventDate}
              onChange={set("eventDate")}
            />
          </Field>
          <Field label="From">
            <input
              className={inputCls}
              type="time"
              required
              value={f.startTime}
              onChange={set("startTime")}
            />
          </Field>
          <Field label="To">
            <input
              className={inputCls}
              type="time"
              required
              value={f.endTime}
              onChange={set("endTime")}
            />
          </Field>
          <Field label="Minutes per parent">
            <select className={inputCls} value={f.slotMinutes} onChange={set("slotMinutes")}>
              {[5, 10, 15, 20, 30].map((m) => (
                <option key={m}>{m}</option>
              ))}
            </select>
          </Field>
          <div className="sm:col-span-2 lg:col-span-4">
            <Field label="Note for parents">
              <input
                className={inputCls}
                maxLength={1000}
                value={f.instructions}
                onChange={set("instructions")}
                placeholder="e.g. Please bring the report card"
              />
            </Field>
          </div>
          <div className="sm:col-span-2 lg:col-span-5">
            <Field label="Teachers: one per line, as Name | Class or subject | Room *">
              <textarea
                className={inputCls}
                required
                rows={5}
                value={f.hosts}
                onChange={set("hosts")}
                placeholder={
                  "Mrs Lakshmi Rao | Class 3A | Room 12\nMr Vivek Sharma | Class 5B, Maths | Room 21"
                }
              />
            </Field>
          </div>
        </div>
        <div className="mt-4 flex gap-2">
          <Btn type="submit" variant="primary" busy={busy === "add"}>
            Create PTM
          </Btn>
          <Btn variant="ghost" onClick={onClose}>
            Cancel
          </Btn>
        </div>
      </form>
    </Card>
  );
}

function ScheduleView({ id, onBack }: { id: string; onBack: () => void }) {
  const [s, setS] = useState<Schedule | null>(null);
  const [hostId, setHostId] = useState<string>("");
  const [moreHosts, setMoreHosts] = useState("");
  const { busy, run } = useAction();

  const load = useCallback(async () => setS(await sb<Schedule>(`engagement/ptm/${id}`)), [id]);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  if (!s) return <Loading />;
  const e = s.event;
  const shown = hostId ? s.hosts.filter((h) => h.id === hostId) : s.hosts;

  async function toggleOpen() {
    if (
      await run(
        "open",
        () =>
          sb(`engagement/ptm/${id}`, { method: "PATCH", body: { bookingOpen: !e.bookingOpen } }),
        e.bookingOpen ? "Booking closed" : "Booking opened",
      )
    )
      void load();
  }
  async function addHosts() {
    if (
      await run(
        "hosts",
        () =>
          sb(`engagement/ptm/${id}`, { method: "PATCH", body: { hosts: parseHosts(moreHosts) } }),
        "Teachers added",
      )
    ) {
      setMoreHosts("");
      void load();
    }
  }
  async function removeHost(h: HostSchedule) {
    if (!confirm(`Remove ${h.name} from this PTM?`)) return;
    if (
      await run(
        "rm" + h.id,
        () => sb(`engagement/ptm/hosts/${h.id}`, { method: "DELETE" }),
        "Removed",
      )
    )
      void load();
  }
  async function status(b: Booking, st: string) {
    if (
      st === "CANCELLED" &&
      !confirm(`Cancel ${b.parentName}'s booking at ${fmtTime(b.slotStart)}?`)
    )
      return;
    if (
      await run("b" + b.id, () =>
        sb(`engagement/ptm/bookings/${b.id}/status`, { body: { status: st } }),
      )
    )
      void load();
  }
  async function remove() {
    if (!confirm(`Delete “${e.title}” and all its bookings?`)) return;
    if (
      await run(
        "del",
        () => sb(`engagement/ptm/${id}`, { method: "DELETE" }).then(() => true),
        "Deleted",
      )
    )
      onBack();
  }

  return (
    <div>
      <button type="button" className="text-primary mb-3 text-sm font-medium" onClick={onBack}>
        ← All PTMs
      </button>
      <Card className="mb-5 print:shadow-none print:ring-0">
        <div className="flex flex-wrap items-start gap-3">
          <div className="min-w-0 flex-1">
            <h2 className="text-lg font-semibold">{e.title}</h2>
            <p className="text-ink-muted text-sm">
              {fmtDay(e.eventDate)} · {fmtTime(e.startTime)}–{fmtTime(e.endTime)} · {e.slotMinutes}{" "}
              min · {e.booked}/{e.slots} booked
            </p>
          </div>
          <div className="flex flex-wrap gap-2 print:hidden">
            <Btn onClick={() => void toggleOpen()} busy={busy === "open"}>
              {e.bookingOpen ? "Close booking" : "Open booking"}
            </Btn>
            <Btn onClick={() => window.print()}>
              <Printer className="size-4" aria-hidden /> Print
            </Btn>
            <Btn
              variant="ghost"
              onClick={() => void remove()}
              busy={busy === "del"}
              aria-label="Delete PTM"
            >
              <Trash2 className="size-4" aria-hidden />
            </Btn>
          </div>
        </div>
        <div className="mt-3 print:hidden">
          <select
            className={`${inputCls} w-auto!`}
            value={hostId}
            onChange={(ev) => setHostId(ev.target.value)}
            aria-label="Show teacher"
          >
            <option value="">All teachers</option>
            {s.hosts.map((h) => (
              <option key={h.id} value={h.id}>
                {h.name}
              </option>
            ))}
          </select>
        </div>
      </Card>
      <div className="grid gap-5 lg:grid-cols-2">
        {shown.map((h) => (
          <Card key={h.id} className="print:break-inside-avoid">
            <div className="flex items-start gap-2">
              <div className="min-w-0 flex-1">
                <h3 className="font-semibold">{h.name}</h3>
                <p className="text-ink-muted text-xs">
                  {[h.label, h.room].filter(Boolean).join(" · ")}
                </p>
              </div>
              {!h.slots.some((x) => x.booking) && (
                <Btn
                  size="sm"
                  variant="ghost"
                  className="print:hidden"
                  busy={busy === "rm" + h.id}
                  onClick={() => void removeHost(h)}
                  aria-label={`Remove ${h.name}`}
                >
                  <X className="size-3.5" aria-hidden />
                </Btn>
              )}
            </div>
            <ul className="mt-3 divide-y divide-black/5 text-sm">
              {h.slots.map((sl) => (
                <li key={sl.start} className="flex flex-wrap items-center gap-x-3 gap-y-1 py-1.5">
                  <span className="w-16 shrink-0 tabular-nums">{fmtTime(sl.start)}</span>
                  {sl.booking ? (
                    <>
                      <span className="min-w-[9rem] flex-1">
                        <span className="font-medium">{sl.booking.studentName}</span>
                        {sl.booking.studentClass && (
                          <span className="text-ink-muted"> ({sl.booking.studentClass})</span>
                        )}
                        <span className="text-ink-muted block text-xs">
                          {sl.booking.parentName} · {sl.booking.phone}
                        </span>
                      </span>
                      <Badge tone={BOOKING_TONE[sl.booking.status]}>
                        {sl.booking.status === "NO_SHOW"
                          ? "No-show"
                          : sl.booking.status === "ATTENDED"
                            ? "Met"
                            : "Booked"}
                      </Badge>
                      {sl.booking.status === "BOOKED" && (
                        <span className="flex gap-1 print:hidden">
                          <Btn
                            size="sm"
                            busy={busy === "b" + sl.booking.id}
                            onClick={() => void status(sl.booking!, "ATTENDED")}
                          >
                            Met
                          </Btn>
                          <Btn
                            size="sm"
                            variant="ghost"
                            onClick={() => void status(sl.booking!, "NO_SHOW")}
                          >
                            No-show
                          </Btn>
                          <Btn
                            size="sm"
                            variant="ghost"
                            onClick={() => void status(sl.booking!, "CANCELLED")}
                            aria-label="Cancel booking"
                          >
                            <X className="size-3.5" aria-hidden />
                          </Btn>
                        </span>
                      )}
                    </>
                  ) : (
                    <span className="text-ink-muted text-xs">Free</span>
                  )}
                </li>
              ))}
            </ul>
          </Card>
        ))}
      </div>
      <Card className="mt-5 print:hidden">
        <Field label="Add teachers (Name | Class or subject | Room, one per line)">
          <textarea
            className={inputCls}
            rows={2}
            value={moreHosts}
            onChange={(ev) => setMoreHosts(ev.target.value)}
          />
        </Field>
        <Btn
          className="mt-2"
          busy={busy === "hosts"}
          disabled={!moreHosts.trim()}
          onClick={() => void addHosts()}
        >
          <Plus className="size-4" aria-hidden /> Add
        </Btn>
      </Card>
    </div>
  );
}
