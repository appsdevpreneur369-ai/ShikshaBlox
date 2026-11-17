"use client";

import { Button } from "@shikshablox/ui";
import { useId, type InputHTMLAttributes, type ReactNode } from "react";
import { useFormStatus } from "react-dom";

const inputClass =
  "block h-11 w-full rounded-xl border border-black/10 bg-white px-3.5 text-base shadow-xs outline-none transition focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-primary/30 aria-[invalid=true]:border-red-500";

export function TextField({
  label,
  hint,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: ReactNode }) {
  const id = useId();
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        className={inputClass}
        aria-describedby={hint ? `${id}-hint` : undefined}
        {...props}
      />
      {hint ? (
        <p id={`${id}-hint`} className="text-ink-muted text-xs">
          {hint}
        </p>
      ) : null}
    </div>
  );
}

/** Six-digit one-time code input (authenticator app or email). */
export function CodeField({ label = "6-digit code" }: { label?: string }) {
  return (
    <TextField
      label={label}
      name="code"
      inputMode="numeric"
      autoComplete="one-time-code"
      pattern="\d{6}"
      maxLength={6}
      required
      className={`${inputClass} text-center font-mono text-xl tracking-[0.5em]`}
    />
  );
}

export function SubmitButton({ children }: { children: ReactNode }) {
  const { pending } = useFormStatus();
  return (
    <Button type="submit" className="w-full" disabled={pending} aria-busy={pending}>
      {pending ? "Please wait…" : children}
    </Button>
  );
}

export function FormMessage({ error, info }: { error?: string; info?: string }) {
  return (
    <div aria-live="polite" className="min-h-5 text-sm">
      {error ? (
        <p role="alert" className="rounded-xl bg-red-50 px-3 py-2 text-red-700 ring-1 ring-red-200">
          {error}
        </p>
      ) : info ? (
        <p
          role="status"
          className="rounded-xl bg-emerald-50 px-3 py-2 text-emerald-800 ring-1 ring-emerald-200"
        >
          {info}
        </p>
      ) : null}
    </div>
  );
}
