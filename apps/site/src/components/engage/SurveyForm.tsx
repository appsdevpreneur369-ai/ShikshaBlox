"use client";

import { useState } from "react";
import { engage } from "./api";

export type Question = {
  id: string;
  type: "RATING" | "CHOICE" | "MULTI" | "TEXT";
  label: string;
  options: string[];
  required: boolean;
};
export type PublicSurvey = {
  title: string;
  description?: string | null;
  questions: Question[];
  anonymous: boolean;
};

export function SurveyForm({ slug, survey }: { slug: string; survey: PublicSurvey }) {
  const [answers, setAnswers] = useState<Record<string, unknown>>({});
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [done, setDone] = useState(false);

  const set = (id: string, v: unknown) => setAnswers((a) => ({ ...a, [id]: v }));

  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const missing = survey.questions.find(
      (q) =>
        q.required &&
        (answers[q.id] == null ||
          answers[q.id] === "" ||
          (Array.isArray(answers[q.id]) && (answers[q.id] as unknown[]).length === 0)),
    );
    if (missing) {
      setError(`Please answer: ${missing.label}`);
      return;
    }
    const fd = Object.fromEntries(new FormData(e.currentTarget).entries()) as Record<
      string,
      string
    >;
    setSending(true);
    setError(null);
    const r = await engage<void>(`surveys/${slug}`, {
      answers,
      name: fd.name,
      phone: fd.phone,
      website: fd.website,
    });
    setSending(false);
    if (r.ok) {
      setDone(true);
      window.scrollTo({ top: 0, behavior: "smooth" });
    } else setError(r.message);
  }

  if (done) {
    return (
      <div role="status" className="sb-card mx-auto max-w-xl p-8 text-center">
        <div className="bg-primary-soft text-primary mx-auto flex h-16 w-16 items-center justify-center rounded-full text-3xl">
          ✓
        </div>
        <h2 className="mt-5 text-2xl font-bold">Thank you!</h2>
        <p className="sb-muted mt-2">Your feedback has been recorded.</p>
      </div>
    );
  }

  return (
    <form onSubmit={(e) => void submit(e)} className="max-w-2xl space-y-8">
      {survey.questions.map((q, i) => (
        <fieldset key={q.id}>
          <legend className="mb-3 text-lg font-bold">
            {i + 1}. {q.label} {q.required && <span aria-hidden="true">*</span>}
            {q.required && <span className="sr-only">(required)</span>}
          </legend>
          {q.type === "RATING" ? (
            <div className="flex flex-wrap gap-2">
              {[1, 2, 3, 4, 5].map((r) => (
                <button
                  key={r}
                  type="button"
                  aria-pressed={answers[q.id] === r}
                  aria-label={`${r} out of 5`}
                  onClick={() => set(q.id, r)}
                  className={`flex size-12 items-center justify-center rounded-full text-lg font-bold ring-1 transition ${answers[q.id] === r ? "bg-primary text-on-primary ring-transparent" : "bg-white ring-black/15 hover:ring-black/30"}`}
                >
                  {r}
                </button>
              ))}
              <span className="sb-muted self-center text-sm">1 = poor · 5 = excellent</span>
            </div>
          ) : q.type === "CHOICE" ? (
            <div className="grid gap-2">
              {q.options.map((o) => (
                <label key={o} className="flex min-h-11 cursor-pointer items-center gap-3">
                  <input
                    type="radio"
                    name={q.id}
                    className="size-5 accent-[var(--sb-color-primary)]"
                    checked={answers[q.id] === o}
                    onChange={() => set(q.id, o)}
                  />
                  <span>{o}</span>
                </label>
              ))}
            </div>
          ) : q.type === "MULTI" ? (
            <div className="grid gap-2">
              {q.options.map((o) => {
                const list = (answers[q.id] as string[] | undefined) ?? [];
                return (
                  <label key={o} className="flex min-h-11 cursor-pointer items-center gap-3">
                    <input
                      type="checkbox"
                      className="size-5 accent-[var(--sb-color-primary)]"
                      checked={list.includes(o)}
                      onChange={(e) =>
                        set(q.id, e.target.checked ? [...list, o] : list.filter((x) => x !== o))
                      }
                    />
                    <span>{o}</span>
                  </label>
                );
              })}
            </div>
          ) : (
            <textarea
              rows={4}
              maxLength={2000}
              aria-label={q.label}
              className="sb-input"
              value={(answers[q.id] as string) ?? ""}
              onChange={(e) => set(q.id, e.target.value)}
            />
          )}
        </fieldset>
      ))}
      {!survey.anonymous && (
        <div className="grid gap-5 sm:grid-cols-2">
          <label className="block">
            <span className="mb-1.5 block text-sm font-semibold">Your name</span>
            <input name="name" maxLength={120} autoComplete="name" className="sb-input" />
          </label>
          <label className="block">
            <span className="mb-1.5 block text-sm font-semibold">Mobile number</span>
            <input
              name="phone"
              type="tel"
              inputMode="tel"
              maxLength={20}
              autoComplete="tel"
              className="sb-input"
            />
          </label>
        </div>
      )}
      <label className="hidden" aria-hidden="true">
        Website <input name="website" tabIndex={-1} autoComplete="off" />
      </label>
      {survey.anonymous && (
        <p className="sb-muted text-sm">This survey is anonymous. Your name is not recorded.</p>
      )}
      {error && (
        <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
          {error}
        </p>
      )}
      <button
        type="submit"
        disabled={sending}
        className="sb-btn sb-btn-primary sb-focus w-full disabled:opacity-60 sm:w-auto"
      >
        {sending ? "Sending…" : "Submit"}
      </button>
    </form>
  );
}
