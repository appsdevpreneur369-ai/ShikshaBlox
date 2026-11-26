"use client";

import { useState } from "react";
import type { ApplicationForm as Form } from "@/lib/api";

type State =
  | { status: "idle" | "sending" }
  | { status: "error"; message: string }
  | { status: "sent"; applicationNo: string; feeAmount: number; feeInstructions?: string | null };

const ACCEPT = "application/pdf,image/jpeg,image/png";
/** Keeps the whole request comfortably under the hosting limit on slow mobile connections. */
const MAX_TOTAL = 25 * 1024 * 1024;

const mb = (n: number) => `${(n / (1024 * 1024)).toFixed(n < 1024 * 1024 ? 2 : 1)} MB`;
const rupees = (n: number) => `₹${n.toLocaleString("en-IN")}`;

/** Phone photos are often 4–8 MB: shrink large JPG/PNG images to 2000px JPEG before upload. */
async function shrink(file: File): Promise<File> {
  if (!/^image\/(jpeg|png)$/.test(file.type) || file.size < 1.5 * 1024 * 1024) return file;
  try {
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, 2000 / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement("canvas");
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    const ctx = canvas.getContext("2d");
    if (!ctx) return file;
    ctx.fillStyle = "#fff";
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise<Blob | null>((r) => canvas.toBlob(r, "image/jpeg", 0.85));
    if (!blob || blob.size >= file.size) return file;
    return new File([blob], file.name.replace(/\.(png|jpe?g)$/i, "") + ".jpg", {
      type: "image/jpeg",
    });
  } catch {
    return file;
  }
}

export function ApplicationForm({ form }: { form: Form }) {
  const [state, setState] = useState<State>({ status: "idle" });
  const [files, setFiles] = useState<Record<string, File | undefined>>({});
  const [fileErr, setFileErr] = useState<Record<string, string | undefined>>({});

  async function pick(code: string, f: File | undefined) {
    if (!f) {
      setFiles((s) => ({ ...s, [code]: undefined }));
      return;
    }
    if (!ACCEPT.split(",").includes(f.type)) {
      setFileErr((s) => ({ ...s, [code]: "Please choose a PDF, JPG or PNG file." }));
      setFiles((s) => ({ ...s, [code]: undefined }));
      return;
    }
    const small = await shrink(f);
    if (small.size > form.maxFileBytes) {
      setFileErr((s) => ({
        ...s,
        [code]: `This file is ${mb(small.size)}. Please upload a copy under ${mb(form.maxFileBytes)}.`,
      }));
      setFiles((s) => ({ ...s, [code]: undefined }));
      return;
    }
    setFileErr((s) => ({ ...s, [code]: undefined }));
    setFiles((s) => ({ ...s, [code]: small }));
  }

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const el = e.currentTarget;
    const missing = form.documents.filter((d) => d.required && !files[d.code]);
    if (missing.length) {
      setState({
        status: "error",
        message: `Please upload: ${missing.map((d) => d.label).join(", ")}.`,
      });
      return;
    }
    const total = Object.values(files).reduce((n, f) => n + (f?.size ?? 0), 0);
    if (total > MAX_TOTAL) {
      setState({
        status: "error",
        message: `The documents add up to ${mb(total)}. Please upload smaller copies (under ${mb(MAX_TOTAL)} in total).`,
      });
      return;
    }
    const fd = new FormData(el);
    const data: Record<string, unknown> = {};
    for (const [k, v] of fd.entries()) if (typeof v === "string") data[k] = v;
    data.consent = data.consent === "on";
    const body = new FormData();
    body.set("data", JSON.stringify(data));
    for (const [code, f] of Object.entries(files)) if (f) body.set(`doc_${code}`, f, f.name);
    setState({ status: "sending" });
    try {
      const res = await fetch("/api/application", { method: "POST", body });
      const out = (await res.json().catch(() => ({}))) as {
        applicationNo?: string;
        feeAmount?: number;
        feeInstructions?: string | null;
        detail?: string;
        message?: string;
      };
      if (res.ok && out.applicationNo) {
        el.reset();
        setFiles({});
        setState({
          status: "sent",
          applicationNo: out.applicationNo,
          feeAmount: out.feeAmount ?? 0,
          feeInstructions: out.feeInstructions,
        });
        window.scrollTo({ top: 0, behavior: "smooth" });
      } else {
        const msg =
          res.status === 429
            ? "Too many attempts. Please wait a few minutes and try again."
            : res.status === 413
              ? "The files are too large. Please upload smaller copies."
              : (out.detail ?? out.message);
        setState({ status: "error", message: msg ?? "Something went wrong. Please try again." });
      }
    } catch {
      setState({
        status: "error",
        message: "We could not reach the school. Please check your connection and try again.",
      });
    }
  }

  if (state.status === "sent") {
    return (
      <div role="status" className="sb-card mx-auto max-w-xl p-8 text-center">
        <div className="bg-primary-soft text-primary mx-auto flex h-16 w-16 items-center justify-center rounded-full text-3xl">
          ✓
        </div>
        <h2 className="mt-5 text-2xl font-bold">Application submitted</h2>
        <p className="sb-muted mt-2">Your application number is</p>
        <p className="text-primary mt-1 text-3xl font-extrabold tracking-wide">
          {state.applicationNo}
        </p>
        <p className="sb-muted mt-3">
          Please note it down and quote it when you contact the school.
        </p>
        {state.feeAmount > 0 && (
          <div className="bg-primary-soft mt-6 rounded-2xl p-4 text-left">
            <p className="font-semibold">Application fee: {rupees(state.feeAmount)}</p>
            {state.feeInstructions && (
              <p className="sb-muted mt-1 text-sm whitespace-pre-line">{state.feeInstructions}</p>
            )}
          </div>
        )}
        <p className="sb-muted mt-6 text-sm">
          The admissions office will review your documents and contact you.
        </p>
      </div>
    );
  }

  const sending = state.status === "sending";
  return (
    <form onSubmit={(e) => void onSubmit(e)} className="space-y-10">
      <fieldset className="grid gap-5 sm:grid-cols-2">
        <legend className="mb-4 text-xl font-bold">Student</legend>
        <label className="block sm:col-span-2">
          <span className="mb-1.5 block text-sm font-semibold">Student&apos;s full name *</span>
          <input
            name="studentName"
            required
            maxLength={120}
            autoComplete="off"
            className="sb-input"
          />
        </label>
        <label className="block">
          <span className="mb-1.5 block text-sm font-semibold">Date of birth *</span>
          <input name="dateOfBirth" required type="date" className="sb-input" />
        </label>
        <label className="block">
          <span className="mb-1.5 block text-sm font-semibold">Gender</span>
          <select name="gender" className="sb-input" defaultValue="">
            <option value="">Prefer not to say</option>
            <option value="FEMALE">Girl</option>
            <option value="MALE">Boy</option>
            <option value="OTHER">Other</option>
          </select>
        </label>
        <label className="block">
          <span className="mb-1.5 block text-sm font-semibold">Admission sought for *</span>
          <select name="grade" required className="sb-input" defaultValue="">
            <option value="" disabled>
              Select a class
            </option>
            {form.grades.map((g) => (
              <option key={g}>{g}</option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="mb-1.5 block text-sm font-semibold">Current / previous school</span>
          <input name="previousSchool" maxLength={200} className="sb-input" />
        </label>
      </fieldset>

      <fieldset className="grid gap-5 sm:grid-cols-2">
        <legend className="mb-4 text-xl font-bold">Parent / guardian</legend>
        <label className="block">
          <span className="mb-1.5 block text-sm font-semibold">Name *</span>
          <input
            name="guardianName"
            required
            maxLength={120}
            autoComplete="name"
            className="sb-input"
          />
        </label>
        <label className="block">
          <span className="mb-1.5 block text-sm font-semibold">Relationship</span>
          <select name="relationship" className="sb-input" defaultValue="">
            <option value="">Select</option>
            <option>Mother</option>
            <option>Father</option>
            <option>Guardian</option>
          </select>
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
          <span className="mb-1.5 block text-sm font-semibold">Email</span>
          <input
            name="email"
            type="email"
            maxLength={200}
            autoComplete="email"
            className="sb-input"
          />
        </label>
        <label className="block sm:col-span-2">
          <span className="mb-1.5 block text-sm font-semibold">Home address *</span>
          <textarea
            name="address"
            required
            rows={3}
            maxLength={500}
            autoComplete="street-address"
            className="sb-input"
          />
        </label>
        <label className="block">
          <span className="mb-1.5 block text-sm font-semibold">Second parent / guardian</span>
          <input name="secondGuardianName" maxLength={120} className="sb-input" />
        </label>
        <label className="block">
          <span className="mb-1.5 block text-sm font-semibold">Their mobile number</span>
          <input
            name="secondGuardianPhone"
            type="tel"
            inputMode="tel"
            maxLength={20}
            pattern="[+0-9 \-]{8,20}"
            className="sb-input"
          />
        </label>
      </fieldset>

      {form.documents.length > 0 && (
        <fieldset>
          <legend className="mb-2 text-xl font-bold">Documents</legend>
          <p className="sb-muted mb-5 text-sm">
            PDF, JPG or PNG, up to {mb(form.maxFileBytes)} each. A clear phone photo is fine.
            Documents are seen only by the admissions office.
          </p>
          <ul className="grid gap-4 sm:grid-cols-2">
            {form.documents.map((d) => {
              const f = files[d.code];
              const id = `doc-${d.code}`;
              return (
                <li key={d.code} className="sb-card p-4">
                  <label htmlFor={id} className="block text-sm font-semibold">
                    {d.label}{" "}
                    {d.required ? "*" : <span className="sb-muted font-normal">(optional)</span>}
                  </label>
                  <input
                    id={id}
                    type="file"
                    accept={ACCEPT}
                    aria-describedby={fileErr[d.code] ? `${id}-err` : undefined}
                    onChange={(e) => void pick(d.code, e.target.files?.[0])}
                    className="file:bg-primary-soft file:text-primary mt-2 block w-full text-sm file:mr-3 file:min-h-11 file:cursor-pointer file:rounded-full file:border-0 file:px-4 file:py-2 file:font-semibold"
                  />
                  {f && (
                    <p className="sb-muted mt-2 truncate text-xs">
                      ✓ {f.name} · {mb(f.size)}
                    </p>
                  )}
                  {fileErr[d.code] && (
                    <p
                      id={`${id}-err`}
                      role="alert"
                      className="mt-2 text-xs font-medium text-red-700"
                    >
                      {fileErr[d.code]}
                    </p>
                  )}
                </li>
              );
            })}
          </ul>
        </fieldset>
      )}

      {form.feeAmount > 0 && (
        <div className="bg-primary-soft rounded-2xl p-5">
          <p className="font-semibold">Application fee: {rupees(form.feeAmount)}</p>
          {form.feeInstructions && (
            <p className="sb-muted mt-1 text-sm whitespace-pre-line">{form.feeInstructions}</p>
          )}
        </div>
      )}

      {/* Honeypot: hidden from people, filled by bots. */}
      <label className="hidden" aria-hidden="true">
        Website <input name="website" tabIndex={-1} autoComplete="off" />
      </label>
      <label className="flex cursor-pointer items-start gap-3 text-sm">
        <input
          type="checkbox"
          name="consent"
          required
          className="mt-0.5 h-6 w-6 shrink-0 accent-[var(--sb-color-primary)]"
        />
        <span className="sb-muted">
          I confirm these details are correct and agree that the school may use them and the
          documents only to process this admission application.
        </span>
      </label>
      {state.status === "error" && (
        <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
          {state.message}
        </p>
      )}
      <button
        type="submit"
        disabled={sending}
        className="sb-btn sb-btn-primary sb-focus w-full disabled:opacity-60 sm:w-auto"
      >
        {sending ? "Submitting… please keep this page open" : "Submit application"}
      </button>
    </form>
  );
}
