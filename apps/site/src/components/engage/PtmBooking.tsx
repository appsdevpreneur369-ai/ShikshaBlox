"use client";

import { useCallback, useEffect, useState } from "react";
import { engage, fmtDate, fmtTime } from "./api";

type Host = {
  id: string;
  name: string;
  label?: string | null;
  room?: string | null;
  free: string[];
};
type PtmEvent = {
  id: string;
  title: string;
  eventDate: string;
  startTime: string;
  endTime: string;
  slotMinutes: number;
  instructions?: string | null;
  hosts: Host[];
};
type Booked = {
  code: string;
  title: string;
  eventDate: string;
  slotStart: string;
  hostName: string;
  room?: string | null;
};

export function PtmBooking() {
  const [events, setEvents] = useState<PtmEvent[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [eventId, setEventId] = useState<string>("");
  const [hostId, setHostId] = useState<string>("");
  const [slot, setSlot] = useState<string>("");
  const [sending, setSending] = useState(false);
  const [booked, setBooked] = useState<Booked | null>(null);
  const [showCancel, setShowCancel] = useState(false);

  const load = useCallback(async () => {
    const r = await engage<PtmEvent[]>("ptm");
    if (r.ok) {
      setEvents(r.data);
      if (r.data.length === 1) setEventId(r.data[0]!.id);
    } else setError(r.message);
  }, []);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  const ev = events?.find((e) => e.id === eventId);
  const host = ev?.hosts.find((h) => h.id === hostId);

  async function book(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = Object.fromEntries(new FormData(e.currentTarget).entries()) as Record<
      string,
      string
    >;
    setSending(true);
    setError(null);
    const r = await engage<Booked>(`ptm/${eventId}/book`, {
      hostId,
      slotStart: slot,
      parentName: fd.parentName,
      phone: fd.phone,
      studentName: fd.studentName,
      studentClass: fd.studentClass,
    });
    setSending(false);
    if (r.ok) {
      setBooked(r.data);
      window.scrollTo({ top: 0, behavior: "smooth" });
    } else {
      setError(r.message);
      if (r.status === 409) {
        setSlot("");
        void load();
      }
    }
  }

  if (booked) {
    return (
      <div role="status" className="sb-card mx-auto max-w-xl p-8 text-center">
        <div className="bg-primary-soft text-primary mx-auto flex h-16 w-16 items-center justify-center rounded-full text-3xl">
          ✓
        </div>
        <h2 className="mt-5 text-2xl font-bold">Your slot is booked</h2>
        <p className="mt-3 text-lg font-semibold">
          {fmtTime(booked.slotStart)} · {fmtDate(booked.eventDate)}
        </p>
        <p className="sb-muted">
          with {booked.hostName}
          {booked.room ? ` · ${booked.room}` : ""}
        </p>
        <p className="sb-muted mt-5">Your booking code</p>
        <p className="text-primary text-3xl font-extrabold tracking-[0.2em]">{booked.code}</p>
        <p className="sb-muted mt-3 text-sm">
          Keep this code. You need it with your phone number to cancel.
        </p>
        <button
          type="button"
          className="sb-btn sb-btn-ghost text-primary mt-6"
          onClick={() => {
            setBooked(null);
            setSlot("");
            setHostId("");
            void load();
          }}
        >
          Book with another teacher
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      {events === null ? (
        error ? (
          <p
            role="alert"
            className="rounded-xl bg-red-50 px-4 py-3 text-sm font-medium text-red-700"
          >
            {error}
          </p>
        ) : (
          <p className="sb-muted">Loading…</p>
        )
      ) : events.length === 0 ? (
        <div className="sb-card p-6">
          <h2 className="text-xl font-bold">No PTM is open for booking right now</h2>
          <p className="sb-muted mt-2">
            The school will announce the next parent-teacher meeting. Please check back then.
          </p>
        </div>
      ) : (
        <form onSubmit={(e) => void book(e)} className="space-y-8">
          {events.length > 1 && (
            <fieldset>
              <legend className="mb-3 text-xl font-bold">1. Choose the PTM</legend>
              <div className="grid gap-3 sm:grid-cols-2">
                {events.map((e) => (
                  <label
                    key={e.id}
                    aria-label={`${e.title}, ${fmtDate(e.eventDate)}`}
                    className={`sb-card flex cursor-pointer items-start gap-3 p-4 ${eventId === e.id ? "ring-2 ring-[var(--sb-color-primary)]" : ""}`}
                  >
                    <input
                      type="radio"
                      name="event"
                      className="mt-1 size-5 accent-[var(--sb-color-primary)]"
                      checked={eventId === e.id}
                      onChange={() => {
                        setEventId(e.id);
                        setHostId("");
                        setSlot("");
                      }}
                    />
                    <span>
                      <span className="block font-semibold">{e.title}</span>
                      <span className="sb-muted block text-sm">{fmtDate(e.eventDate)}</span>
                    </span>
                  </label>
                ))}
              </div>
            </fieldset>
          )}
          {ev && (
            <>
              <div className="sb-card p-5">
                <h2 className="text-xl font-bold">{ev.title}</h2>
                <p className="sb-muted">
                  {fmtDate(ev.eventDate)} · {fmtTime(ev.startTime)}–{fmtTime(ev.endTime)} ·{" "}
                  {ev.slotMinutes}-minute slots
                </p>
                {ev.instructions && (
                  <p className="sb-muted mt-2 text-sm whitespace-pre-line">{ev.instructions}</p>
                )}
              </div>
              <fieldset>
                <legend className="mb-3 text-xl font-bold">
                  {events.length > 1 ? "2" : "1"}. Choose the teacher
                </legend>
                <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                  {ev.hosts.map((h) => (
                    <label
                      key={h.id}
                      aria-label={`${h.name}${h.label ? `, ${h.label}` : ""}: ${h.free.length === 0 ? "fully booked" : `${h.free.length} slots free`}`}
                      className={`sb-card flex cursor-pointer items-start gap-3 p-4 ${hostId === h.id ? "ring-2 ring-[var(--sb-color-primary)]" : ""} ${h.free.length === 0 ? "opacity-60" : ""}`}
                    >
                      <input
                        type="radio"
                        name="host"
                        className="mt-1 size-5 accent-[var(--sb-color-primary)]"
                        disabled={h.free.length === 0}
                        checked={hostId === h.id}
                        onChange={() => {
                          setHostId(h.id);
                          setSlot("");
                        }}
                      />
                      <span>
                        <span className="block font-semibold">{h.name}</span>
                        <span className="sb-muted block text-sm">
                          {[h.label, h.room].filter(Boolean).join(" · ")}
                        </span>
                        <span className="sb-muted block text-xs">
                          {h.free.length === 0 ? "Fully booked" : `${h.free.length} slots free`}
                        </span>
                      </span>
                    </label>
                  ))}
                </div>
              </fieldset>
            </>
          )}
          {host && (
            <fieldset>
              <legend className="mb-3 text-xl font-bold">
                {events.length > 1 ? "3" : "2"}. Choose a time
              </legend>
              <div className="flex flex-wrap gap-2">
                {host.free.map((t) => (
                  <button
                    key={t}
                    type="button"
                    aria-pressed={slot === t}
                    onClick={() => setSlot(t)}
                    className={`min-h-11 rounded-full px-4 text-sm font-semibold ring-1 transition ${slot === t ? "bg-primary text-on-primary ring-transparent" : "bg-white ring-black/15 hover:ring-black/30"}`}
                  >
                    {fmtTime(t)}
                  </button>
                ))}
              </div>
            </fieldset>
          )}
          {slot && (
            <fieldset className="grid gap-5 sm:grid-cols-2">
              <legend className="mb-4 text-xl font-bold">
                {events.length > 1 ? "4" : "3"}. Your details
              </legend>
              <label className="block">
                <span className="mb-1.5 block text-sm font-semibold">Parent / guardian name *</span>
                <input
                  name="parentName"
                  required
                  maxLength={120}
                  autoComplete="name"
                  className="sb-input"
                />
              </label>
              <label className="block">
                <span className="mb-1.5 block text-sm font-semibold">Mobile number *</span>
                <input
                  name="phone"
                  required
                  type="tel"
                  inputMode="tel"
                  maxLength={20}
                  pattern="[+0-9 \-]{8,20}"
                  autoComplete="tel"
                  className="sb-input"
                />
              </label>
              <label className="block">
                <span className="mb-1.5 block text-sm font-semibold">Student&apos;s name *</span>
                <input name="studentName" required maxLength={120} className="sb-input" />
              </label>
              <label className="block">
                <span className="mb-1.5 block text-sm font-semibold">Class / section</span>
                <input
                  name="studentClass"
                  maxLength={60}
                  className="sb-input"
                  placeholder="e.g. 3A"
                />
              </label>
              <div className="sm:col-span-2">
                {error && (
                  <p
                    role="alert"
                    className="mb-4 rounded-xl bg-red-50 px-4 py-3 text-sm font-medium text-red-700"
                  >
                    {error}
                  </p>
                )}
                <button
                  type="submit"
                  disabled={sending}
                  className="sb-btn sb-btn-primary sb-focus w-full disabled:opacity-60 sm:w-auto"
                >
                  {sending
                    ? "Booking…"
                    : `Book ${fmtTime(slot)} with ${host?.name ?? "the teacher"}`}
                </button>
              </div>
            </fieldset>
          )}
          {!slot && error && (
            <p
              role="alert"
              className="rounded-xl bg-red-50 px-4 py-3 text-sm font-medium text-red-700"
            >
              {error}
            </p>
          )}
        </form>
      )}

      <div className="border-t border-black/10 pt-6">
        {showCancel ? (
          <CancelForm onDone={() => void load()} />
        ) : (
          <button
            type="button"
            className="text-primary text-sm font-semibold underline"
            onClick={() => setShowCancel(true)}
          >
            Need to cancel a booking?
          </button>
        )}
      </div>
    </div>
  );
}

function CancelForm({ onDone }: { onDone: () => void }) {
  const [state, setState] = useState<{
    status: "idle" | "sending" | "done" | "error";
    message?: string;
  }>({ status: "idle" });
  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = Object.fromEntries(new FormData(e.currentTarget).entries()) as Record<
      string,
      string
    >;
    setState({ status: "sending" });
    const r = await engage<void>("ptm/cancel", { code: fd.code, phone: fd.phone });
    if (r.ok) {
      setState({ status: "done" });
      onDone();
    } else
      setState({
        status: "error",
        message: r.status === 404 ? "No booking matches that code and phone number." : r.message,
      });
  }
  if (state.status === "done")
    return (
      <p role="status" className="font-semibold">
        Your booking has been cancelled.
      </p>
    );
  return (
    <form
      onSubmit={(e) => void submit(e)}
      className="grid max-w-xl gap-4 sm:grid-cols-[1fr_1fr_auto] sm:items-end"
    >
      <label className="block">
        <span className="mb-1.5 block text-sm font-semibold">Booking code</span>
        <input
          name="code"
          required
          maxLength={12}
          autoCapitalize="characters"
          className="sb-input uppercase"
        />
      </label>
      <label className="block">
        <span className="mb-1.5 block text-sm font-semibold">Mobile number</span>
        <input
          name="phone"
          required
          type="tel"
          inputMode="tel"
          maxLength={20}
          className="sb-input"
        />
      </label>
      <button
        type="submit"
        disabled={state.status === "sending"}
        className="sb-btn sb-btn-ghost sb-focus text-primary disabled:opacity-60"
      >
        Cancel booking
      </button>
      {state.status === "error" && (
        <p role="alert" className="text-sm font-medium text-red-700 sm:col-span-3">
          {state.message}
        </p>
      )}
    </form>
  );
}
