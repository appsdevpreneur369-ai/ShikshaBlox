"use client";

import { useState } from "react";

const GRADES = [
  "Pre-KG / Nursery",
  "LKG",
  "UKG",
  ...Array.from({ length: 10 }, (_, i) => `Class ${i + 1}`),
  "Other",
];

type State = { status: "idle" | "sending" | "sent" | "error"; message?: string };

export function EnquiryForm() {
  const [state, setState] = useState<State>({ status: "idle" });

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = Object.fromEntries(new FormData(form).entries());
    setState({ status: "sending" });
    try {
      const res = await fetch("/api/enquiry", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...data, consent: data.consent === "on", pageUrl: window.location.pathname }),
      });
      if (res.ok) {
        form.reset();
        setState({ status: "sent" });
      } else {
        const body = (await res.json().catch(() => ({}))) as { message?: string; detail?: string };
        const msg = res.status === 429 ? "Too many attempts. Please wait a few minutes and try again." : (body.detail ?? body.message);
        setState({ status: "error", message: msg ?? "Something went wrong. Please try again." });
      }
    } catch {
      setState({ status: "error", message: "We could not reach the school. Please check your connection." });
    }
  }

  if (state.status === "sent") {
    return (
      <div role="status" className="py-8 text-center">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-primary-soft text-3xl text-primary">✓</div>
        <h3 className="mt-5 text-2xl font-bold">Thank you!</h3>
        <p className="sb-muted mt-2">We have received your enquiry and will get back to you soon.</p>
        <button type="button" className="sb-btn sb-btn-ghost mt-6 text-primary" onClick={() => setState({ status: "idle" })}>
          Send another enquiry
        </button>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} className="grid gap-5 sm:grid-cols-2" noValidate={false}>
      <label className="block">
        <span className="mb-1.5 block text-sm font-semibold">Parent / guardian name *</span>
        <input name="name" required maxLength={120} autoComplete="name" className="sb-input" />
      </label>
      <label className="block">
        <span className="mb-1.5 block text-sm font-semibold">Phone *</span>
        <input name="phone" required type="tel" maxLength={20} pattern="[+0-9 \-]{8,20}" autoComplete="tel" className="sb-input" />
      </label>
      <label className="block">
        <span className="mb-1.5 block text-sm font-semibold">Email</span>
        <input name="email" type="email" maxLength={200} autoComplete="email" className="sb-input" />
      </label>
      <label className="block">
        <span className="mb-1.5 block text-sm font-semibold">Admission sought for</span>
        <select name="gradeSought" className="sb-input" defaultValue="">
          <option value="">Select a class</option>
          {GRADES.map((g) => (
            <option key={g}>{g}</option>
          ))}
        </select>
      </label>
      <label className="block sm:col-span-2">
        <span className="mb-1.5 block text-sm font-semibold">Your message *</span>
        <textarea name="message" required rows={4} maxLength={2000} className="sb-input" />
      </label>
      {/* Honeypot: hidden from people, filled by bots. */}
      <label className="hidden" aria-hidden="true">
        Website <input name="website" tabIndex={-1} autoComplete="off" />
      </label>
      <label className="flex cursor-pointer items-start gap-3 text-sm sm:col-span-2">
        <input type="checkbox" name="consent" required className="mt-0.5 h-6 w-6 shrink-0 accent-[var(--sb-color-primary)]" />
        <span className="sb-muted">
          I agree that the school may use these details to respond to my enquiry. They will not be shared with anyone else.
        </span>
      </label>
      {state.status === "error" && (
        <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-sm font-medium text-red-700 sm:col-span-2">
          {state.message}
        </p>
      )}
      <div className="sm:col-span-2">
        <button type="submit" disabled={state.status === "sending"} className="sb-btn sb-btn-primary sb-focus disabled:opacity-60">
          {state.status === "sending" ? "Sending…" : "Send enquiry"}
        </button>
      </div>
    </form>
  );
}
