"use client";

import { useState } from "react";
import { engage, fmtWhen } from "./api";

type PublicTicket = {
  ticketNo: string;
  category: string;
  subject: string;
  status: string;
  createdAt: string;
  messages: { from: string; body: string; createdAt: string }[];
};

const CATEGORIES: [string, string][] = [
  ["FEES", "Fees & payments"],
  ["TRANSPORT", "School bus / transport"],
  ["ACADEMICS", "Studies & homework"],
  ["ADMISSIONS", "Admissions"],
  ["CERTIFICATES", "Certificates / TC"],
  ["FACILITIES", "Facilities"],
  ["COMPLAINT", "Complaint"],
  ["OTHER", "Something else"],
];
const STATUS: Record<string, string> = {
  OPEN: "Received",
  IN_PROGRESS: "Being worked on",
  WAITING: "Waiting for your reply",
  RESOLVED: "Resolved",
  CLOSED: "Closed",
};

export function Helpdesk() {
  const [tab, setTab] = useState<"raise" | "track">("raise");
  const [ticket, setTicket] = useState<{ t: PublicTicket; phone: string } | null>(null);
  return (
    <div>
      <div className="mb-8 flex flex-wrap gap-2" role="tablist">
        {(
          [
            ["raise", "Raise a request"],
            ["track", "Track a request"],
          ] as const
        ).map(([k, label]) => (
          <button
            key={k}
            type="button"
            role="tab"
            aria-selected={tab === k}
            onClick={() => setTab(k)}
            className={`min-h-11 rounded-full px-5 text-sm font-semibold ring-1 transition ${tab === k ? "bg-primary text-on-primary ring-transparent" : "bg-white ring-black/15"}`}
          >
            {label}
          </button>
        ))}
      </div>
      {ticket ? (
        <TicketView
          ticket={ticket.t}
          phone={ticket.phone}
          onUpdate={(t) => setTicket({ t, phone: ticket.phone })}
          onClose={() => setTicket(null)}
        />
      ) : tab === "raise" ? (
        <RaiseForm onCreated={(t, phone) => setTicket({ t, phone })} />
      ) : (
        <TrackForm onFound={(t, phone) => setTicket({ t, phone })} />
      )}
    </div>
  );
}

function RaiseForm({ onCreated }: { onCreated: (t: PublicTicket, phone: string) => void }) {
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.currentTarget).entries()) as Record<
      string,
      string
    >;
    setSending(true);
    setError(null);
    const r = await engage<PublicTicket>("helpdesk", { ...data, consent: data.consent === "on" });
    setSending(false);
    if (r.ok) {
      onCreated(r.data, data.phone ?? "");
      window.scrollTo({ top: 0, behavior: "smooth" });
    } else setError(r.message);
  }
  return (
    <form onSubmit={(e) => void submit(e)} className="grid gap-5 sm:grid-cols-2">
      <label className="block">
        <span className="mb-1.5 block text-sm font-semibold">Your name *</span>
        <input
          name="requesterName"
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
        <span className="mb-1.5 block text-sm font-semibold">Email (for replies)</span>
        <input
          name="email"
          type="email"
          maxLength={200}
          autoComplete="email"
          className="sb-input"
        />
      </label>
      <label className="block">
        <span className="mb-1.5 block text-sm font-semibold">About</span>
        <select name="category" className="sb-input" defaultValue="OTHER">
          {CATEGORIES.map(([k, v]) => (
            <option key={k} value={k}>
              {v}
            </option>
          ))}
        </select>
      </label>
      <label className="block">
        <span className="mb-1.5 block text-sm font-semibold">Student&apos;s name</span>
        <input name="studentName" maxLength={120} className="sb-input" />
      </label>
      <label className="block">
        <span className="mb-1.5 block text-sm font-semibold">Class / section</span>
        <input name="studentClass" maxLength={60} className="sb-input" />
      </label>
      <label className="block sm:col-span-2">
        <span className="mb-1.5 block text-sm font-semibold">Subject *</span>
        <input name="subject" required maxLength={160} className="sb-input" />
      </label>
      <label className="block sm:col-span-2">
        <span className="mb-1.5 block text-sm font-semibold">Details *</span>
        <textarea name="description" required rows={5} maxLength={4000} className="sb-input" />
      </label>
      <label className="hidden" aria-hidden="true">
        Website <input name="website" tabIndex={-1} autoComplete="off" />
      </label>
      <label className="flex cursor-pointer items-start gap-3 text-sm sm:col-span-2">
        <input
          type="checkbox"
          name="consent"
          required
          className="mt-0.5 h-6 w-6 shrink-0 accent-[var(--sb-color-primary)]"
        />
        <span className="sb-muted">
          I agree that the school may use these details to respond to my request.
        </span>
      </label>
      {error && (
        <p
          role="alert"
          className="rounded-xl bg-red-50 px-4 py-3 text-sm font-medium text-red-700 sm:col-span-2"
        >
          {error}
        </p>
      )}
      <div className="sm:col-span-2">
        <button
          type="submit"
          disabled={sending}
          className="sb-btn sb-btn-primary sb-focus w-full disabled:opacity-60 sm:w-auto"
        >
          {sending ? "Sending…" : "Send request"}
        </button>
      </div>
    </form>
  );
}

function TrackForm({ onFound }: { onFound: (t: PublicTicket, phone: string) => void }) {
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.currentTarget).entries()) as Record<
      string,
      string
    >;
    setSending(true);
    setError(null);
    const r = await engage<PublicTicket>("helpdesk/lookup", {
      ticketNo: data.ticketNo,
      phone: data.phone,
    });
    setSending(false);
    if (r.ok) onFound(r.data, data.phone ?? "");
    else setError(r.status === 404 ? "No request matches that number and phone." : r.message);
  }
  return (
    <form onSubmit={(e) => void submit(e)} className="grid max-w-2xl gap-5 sm:grid-cols-2">
      <label className="block">
        <span className="mb-1.5 block text-sm font-semibold">Request number</span>
        <input
          name="ticketNo"
          required
          maxLength={20}
          placeholder="HD-00012"
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
      {error && (
        <p
          role="alert"
          className="rounded-xl bg-red-50 px-4 py-3 text-sm font-medium text-red-700 sm:col-span-2"
        >
          {error}
        </p>
      )}
      <div className="sm:col-span-2">
        <button
          type="submit"
          disabled={sending}
          className="sb-btn sb-btn-primary sb-focus disabled:opacity-60"
        >
          {sending ? "Looking…" : "Show status"}
        </button>
      </div>
    </form>
  );
}

function TicketView({
  ticket,
  phone,
  onUpdate,
  onClose,
}: {
  ticket: PublicTicket;
  phone: string;
  onUpdate: (t: PublicTicket) => void;
  onClose: () => void;
}) {
  const [reply, setReply] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  async function send() {
    setSending(true);
    setError(null);
    const r = await engage<PublicTicket>("helpdesk/reply", {
      ticketNo: ticket.ticketNo,
      phone,
      body: reply,
    });
    setSending(false);
    if (r.ok) {
      onUpdate(r.data);
      setReply("");
    } else setError(r.message);
  }
  return (
    <div role="status" className="sb-card max-w-2xl p-6">
      <p className="sb-muted text-sm">Your request number</p>
      <p className="text-primary text-3xl font-extrabold tracking-wide">{ticket.ticketNo}</p>
      <p className="mt-1 text-sm">
        Status: <strong>{STATUS[ticket.status] ?? ticket.status}</strong>
      </p>
      <p className="sb-muted mt-1 text-sm">
        Note this number. You can check the status here with your phone number.
      </p>
      <h2 className="mt-6 text-lg font-bold">{ticket.subject}</h2>
      <ol className="mt-3 space-y-3">
        {ticket.messages.map((m, i) => (
          <li
            key={i}
            className={`rounded-2xl p-3 text-sm ${m.from === "School" ? "bg-primary-soft" : "bg-black/[0.03]"}`}
          >
            <p className="font-semibold">
              {m.from} <span className="sb-muted font-normal">· {fmtWhen(m.createdAt)}</span>
            </p>
            <p className="mt-1 whitespace-pre-line">{m.body}</p>
          </li>
        ))}
      </ol>
      {ticket.status !== "CLOSED" && (
        <div className="mt-5">
          <label className="block">
            <span className="mb-1.5 block text-sm font-semibold">Add a message</span>
            <textarea
              rows={3}
              maxLength={4000}
              className="sb-input"
              value={reply}
              onChange={(e) => setReply(e.target.value)}
            />
          </label>
          {error && (
            <p role="alert" className="mt-2 text-sm font-medium text-red-700">
              {error}
            </p>
          )}
          <button
            type="button"
            disabled={sending || !reply.trim()}
            onClick={() => void send()}
            className="sb-btn sb-btn-primary sb-focus mt-3 disabled:opacity-60"
          >
            {sending ? "Sending…" : "Send"}
          </button>
        </div>
      )}
      <button type="button" className="sb-btn sb-btn-ghost text-primary mt-4" onClick={onClose}>
        Done
      </button>
    </div>
  );
}
