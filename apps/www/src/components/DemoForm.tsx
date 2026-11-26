"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { ROLES, STUDENT_BANDS, validateDemoRequest, type DemoRequest, type FieldErrors } from "@/lib/demo-request";
import { Icon } from "./Icon";

const PLANS = [
  ["web-free", "Web Free: school website"],
  ["web-pro", "Web Pro: website with own domain"],
  ["lite", "Lite: small school ERP"],
  ["standard", "Standard: medium school ERP"],
  ["global", "Global: international school"],
  ["seva", "Seva: government / trust"],
  ["not-sure", "Not sure yet"],
] as const;

const input =
  "mt-1.5 block min-h-12 w-full rounded-xl border border-line bg-bg px-4 text-base text-ink placeholder:text-ink-3 transition-colors focus:border-brand focus:outline-none focus:ring-4 focus:ring-[color-mix(in_oklab,var(--c-brand)_18%,transparent)] aria-[invalid=true]:border-red-500";

export function DemoForm() {
  const planRef = useRef<HTMLSelectElement>(null);
  // Pre-select the plan from ?plan= (read in the browser so the page itself stays static).
  useEffect(() => {
    const plan = new URLSearchParams(window.location.search).get("plan");
    if (plan && planRef.current && PLANS.some(([v]) => v === plan)) planRef.current.value = plan;
  }, []);
  const [state, setState] = useState<"idle" | "sending" | "sent" | "error">("idle");
  const [errors, setErrors] = useState<FieldErrors>({});
  const [message, setMessage] = useState("");

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const data: DemoRequest = {
      name: String(fd.get("name") ?? ""),
      school: String(fd.get("school") ?? ""),
      role: String(fd.get("role") ?? ""),
      phone: String(fd.get("phone") ?? ""),
      email: String(fd.get("email") ?? ""),
      city: String(fd.get("city") ?? ""),
      students: String(fd.get("students") ?? ""),
      plan: String(fd.get("plan") ?? ""),
      message: String(fd.get("message") ?? ""),
      consent: fd.get("consent") === "on",
      website: String(fd.get("website") ?? ""),
    };
    const v = validateDemoRequest(data);
    setErrors(v);
    if (Object.keys(v).length > 0) {
      setMessage("Please check the highlighted fields.");
      setState("error");
      const first = Object.keys(v)[0] ?? "";
      (e.currentTarget.elements.namedItem(first) as HTMLElement | null)?.focus();
      return;
    }
    setState("sending");
    setMessage("");
    try {
      const res = await fetch("/api/demo-request", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
      });
      const body = (await res.json().catch(() => ({}))) as { message?: string; errors?: FieldErrors };
      if (res.ok) {
        setState("sent");
        return;
      }
      setErrors(body.errors ?? {});
      setMessage(body.message ?? "Something went wrong. Please try again.");
      setState("error");
    } catch {
      setMessage("You seem to be offline. Please check your connection and try again.");
      setState("error");
    }
  }

  if (state === "sent") {
    return (
      <div role="status" className="card p-8 text-center sm:p-10">
        <div className="mx-auto grid size-14 place-items-center rounded-full bg-live-soft text-live">
          <Icon name="check" className="size-7" strokeWidth={2.4} />
        </div>
        <h2 className="mt-5 font-display text-2xl font-extrabold text-ink">Thank you! We&apos;ll be in touch.</h2>
        <p className="mt-3 text-ink-2">
          Our team will call you within one working day to schedule your demo. Meanwhile, take a look at a sample school website
          built on ShikshaBlox.
        </p>
        <a href="/schools" className="mt-6 inline-flex min-h-12 items-center gap-2 rounded-full bg-brand px-6 font-semibold text-brand-ink">
          See the demo school website <Icon name="arrow" className="size-4" />
        </a>
      </div>
    );
  }

  const err = (k: keyof DemoRequest) =>
    errors[k] ? (
      <p id={`${k}-error`} className="mt-1.5 text-sm font-medium text-red-600 dark:text-red-400">
        {errors[k]}
      </p>
    ) : null;
  const aria = (k: keyof DemoRequest) => ({
    "aria-invalid": errors[k] ? true : undefined,
    "aria-describedby": errors[k] ? `${k}-error` : undefined,
  });

  return (
    <form noValidate onSubmit={onSubmit} className="card p-6 sm:p-8" aria-label="Book a demo">
      <div className="grid gap-5 sm:grid-cols-2">
        <div>
          <label htmlFor="name" className="text-sm font-semibold text-ink">
            Your name <span className="text-red-600">*</span>
          </label>
          <input id="name" name="name" autoComplete="name" required className={input} {...aria("name")} />
          {err("name")}
        </div>
        <div>
          <label htmlFor="role" className="text-sm font-semibold text-ink">
            Your role
          </label>
          <select id="role" name="role" className={input} defaultValue="">
            <option value="">Select…</option>
            {ROLES.map((r) => (
              <option key={r}>{r}</option>
            ))}
          </select>
        </div>
        <div className="sm:col-span-2">
          <label htmlFor="school" className="text-sm font-semibold text-ink">
            School name <span className="text-red-600">*</span>
          </label>
          <input id="school" name="school" autoComplete="organization" required className={input} {...aria("school")} />
          {err("school")}
        </div>
        <div>
          <label htmlFor="phone" className="text-sm font-semibold text-ink">
            Mobile / WhatsApp <span className="text-red-600">*</span>
          </label>
          <input id="phone" name="phone" type="tel" inputMode="tel" autoComplete="tel" placeholder="+91" required className={input} {...aria("phone")} />
          {err("phone")}
        </div>
        <div>
          <label htmlFor="email" className="text-sm font-semibold text-ink">
            Email
          </label>
          <input id="email" name="email" type="email" autoComplete="email" className={input} {...aria("email")} />
          {err("email")}
        </div>
        <div>
          <label htmlFor="city" className="text-sm font-semibold text-ink">
            City
          </label>
          <input id="city" name="city" autoComplete="address-level2" className={input} {...aria("city")} />
          {err("city")}
        </div>
        <div>
          <label htmlFor="students" className="text-sm font-semibold text-ink">
            Number of students
          </label>
          <select id="students" name="students" className={input} defaultValue="">
            <option value="">Select…</option>
            {STUDENT_BANDS.map((b) => (
              <option key={b}>{b}</option>
            ))}
          </select>
        </div>
        <div className="sm:col-span-2">
          <label htmlFor="plan" className="text-sm font-semibold text-ink">
            I&apos;m interested in
          </label>
          <select id="plan" name="plan" ref={planRef} className={input} defaultValue="">
            <option value="">Select…</option>
            {PLANS.map(([v, l]) => (
              <option key={v} value={v}>
                {l}
              </option>
            ))}
          </select>
        </div>
        <div className="sm:col-span-2">
          <label htmlFor="message" className="text-sm font-semibold text-ink">
            Anything we should know? <span className="font-normal text-ink-3">(optional)</span>
          </label>
          <textarea id="message" name="message" rows={4} className={`${input} py-3`} {...aria("message")} />
          {err("message")}
        </div>
        {/* Honeypot, hidden from people and assistive tech. */}
        <div aria-hidden="true" className="absolute -left-[9999px] h-px w-px overflow-hidden">
          <label htmlFor="website">Website</label>
          <input id="website" name="website" tabIndex={-1} autoComplete="off" />
        </div>
        <div className="sm:col-span-2">
          <label className="flex items-start gap-3 text-sm leading-relaxed text-ink-2">
            <input
              type="checkbox"
              name="consent"
              className="mt-0.5 size-5 shrink-0 accent-[var(--c-brand)]"
              {...aria("consent")}
            />
            <span>
              I agree that ShikshaBlox (Aurora Blossom) may contact me by phone, WhatsApp or email about this demo request. See
              our{" "}
              <a href="/privacy" className="font-semibold text-brand underline underline-offset-2">
                privacy policy
              </a>
              . <span className="text-red-600">*</span>
            </span>
          </label>
          {err("consent")}
        </div>
      </div>
      {state === "error" && message && (
        <p role="alert" className="mt-5 rounded-xl bg-red-50 px-4 py-3 text-sm font-medium text-red-700 dark:bg-red-950 dark:text-red-200">
          {message}
        </p>
      )}
      <button
        type="submit"
        disabled={state === "sending"}
        className="mt-6 inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-full bg-brand px-6 font-semibold text-brand-ink transition-colors hover:bg-brand-strong disabled:opacity-70 sm:w-auto"
      >
        {state === "sending" ? "Sending…" : "Request my free demo"}
        {state !== "sending" && <Icon name="arrow" className="size-4" />}
      </button>
      <p className="mt-3 text-xs text-ink-3">No spam. We reply within one working day.</p>
    </form>
  );
}
