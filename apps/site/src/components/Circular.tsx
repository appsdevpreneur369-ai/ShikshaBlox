"use client";

import { useEffect, useState } from "react";

type CircularData = {
  school: string;
  title: string;
  details?: string | null;
  sentAt?: string | null;
  ackRequired: boolean;
  consentQuestion?: string | null;
  acknowledgedAt?: string | null;
  consentAnswer?: boolean | null;
  stopped: boolean;
};

const when = (iso: string) =>
  new Intl.DateTimeFormat("en-IN", {
    timeZone: "Asia/Kolkata",
    day: "numeric",
    month: "long",
    year: "numeric",
  }).format(new Date(iso));

export function Circular({ token }: { token: string }) {
  const [c, setC] = useState<CircularData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [answer, setAnswer] = useState<boolean | null>(null);
  const [confirmStop, setConfirmStop] = useState(false);

  useEffect(() => {
    let live = true;
    fetch(`/api/circular/${token}`, { cache: "no-store" })
      .then(async (r) => {
        if (!live) return;
        if (r.ok) setC((await r.json()) as CircularData);
        else
          setError(
            r.status === 404
              ? "This link is not valid. Please check the message from the school."
              : "Please try again in a few minutes.",
          );
      })
      .catch(() => {
        if (live) setError("We could not reach the school. Please check your connection.");
      });
    return () => {
      live = false;
    };
  }, [token]);

  async function post(action: "ack" | "stop", body: object = {}) {
    setBusy(true);
    try {
      const r = await fetch(`/api/circular/${token}/${action}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      if (r.ok) setC((await r.json()) as CircularData);
      else setError("Something went wrong. Please try again.");
    } catch {
      setError("We could not reach the school. Please check your connection.");
    } finally {
      setBusy(false);
    }
  }

  if (error) {
    return (
      <p
        role="alert"
        className="mx-auto max-w-2xl rounded-xl bg-red-50 px-4 py-3 font-medium text-red-700"
      >
        {error}
      </p>
    );
  }
  if (!c) return <p className="sb-muted mx-auto max-w-2xl">Loading…</p>;

  return (
    <article className="sb-card mx-auto max-w-2xl p-6 sm:p-8">
      <p className="sb-eyebrow">{c.school}</p>
      <h1 className="mt-2 text-3xl font-extrabold">{c.title}</h1>
      {c.sentAt && <p className="sb-muted mt-1 text-sm">{when(c.sentAt)}</p>}
      {c.details && (
        <div className="mt-5 text-lg leading-relaxed whitespace-pre-line">{c.details}</div>
      )}

      {c.ackRequired && (
        <div className="bg-primary-soft mt-8 rounded-2xl p-5">
          {c.acknowledgedAt ? (
            <p role="status" className="font-semibold">
              ✓ Acknowledged on {when(c.acknowledgedAt)}
              {c.consentQuestion && c.consentAnswer != null
                ? ` · Your answer: ${c.consentAnswer ? "Yes" : "No"}`
                : ""}
            </p>
          ) : (
            <>
              {c.consentQuestion && (
                <fieldset className="mb-4">
                  <legend className="mb-2 font-semibold">{c.consentQuestion}</legend>
                  <div className="flex gap-3">
                    <label className="flex min-h-11 cursor-pointer items-center gap-2 rounded-full bg-white px-4 ring-1 ring-black/15">
                      <input
                        type="radio"
                        name="consent"
                        className="size-5 accent-[var(--sb-color-primary)]"
                        checked={answer === true}
                        onChange={() => setAnswer(true)}
                      />
                      Yes
                    </label>
                    <label className="flex min-h-11 cursor-pointer items-center gap-2 rounded-full bg-white px-4 ring-1 ring-black/15">
                      <input
                        type="radio"
                        name="consent"
                        className="size-5 accent-[var(--sb-color-primary)]"
                        checked={answer === false}
                        onChange={() => setAnswer(false)}
                      />
                      No
                    </label>
                  </div>
                </fieldset>
              )}
              <button
                type="button"
                disabled={busy || (c.consentQuestion != null && answer === null)}
                onClick={() => void post("ack", { consent: c.consentQuestion ? answer : null })}
                className="sb-btn sb-btn-primary sb-focus w-full disabled:opacity-60 sm:w-auto"
              >
                {busy ? "Saving…" : "I have read this"}
              </button>
            </>
          )}
        </div>
      )}

      <div className="sb-muted mt-10 border-t border-black/10 pt-4 text-sm">
        {c.stopped ? (
          <p>
            You will not receive further messages from {c.school}. Contact the school office to
            start again.
          </p>
        ) : confirmStop ? (
          <p>
            Stop all WhatsApp, SMS and email messages from {c.school}?{" "}
            <button
              type="button"
              className="font-semibold text-red-700 underline"
              disabled={busy}
              onClick={() => void post("stop")}
            >
              Yes, stop
            </button>{" "}
            <button
              type="button"
              className="font-semibold underline"
              onClick={() => setConfirmStop(false)}
            >
              Cancel
            </button>
          </p>
        ) : (
          <button type="button" className="underline" onClick={() => setConfirmStop(true)}>
            Don&apos;t want these messages?
          </button>
        )}
      </div>
    </article>
  );
}
