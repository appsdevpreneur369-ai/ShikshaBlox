"use client";

import { Button } from "@shikshablox/ui";
import { useId, useState } from "react";

/** M0 shell: the form is accessible and validated client-side; authentication is wired in M1a. */
export function LoginForm() {
  const emailId = useId();
  const passwordId = useId();
  const [message, setMessage] = useState<string | null>(null);

  return (
    <form
      className="mt-8 space-y-5"
      noValidate={false}
      onSubmit={(e) => {
        e.preventDefault();
        setMessage("Sign-in will be available in the next milestone.");
      }}
    >
      <div className="space-y-1.5">
        <label htmlFor={emailId} className="text-sm font-medium">
          Email
        </label>
        <input
          id={emailId}
          name="email"
          type="email"
          autoComplete="username"
          required
          className="focus-visible:border-primary focus-visible:ring-primary/30 block h-11 w-full rounded-xl border border-black/10 bg-white px-3.5 text-base shadow-xs outline-none focus-visible:ring-2"
        />
      </div>
      <div className="space-y-1.5">
        <label htmlFor={passwordId} className="text-sm font-medium">
          Password
        </label>
        <input
          id={passwordId}
          name="password"
          type="password"
          autoComplete="current-password"
          required
          className="focus-visible:border-primary focus-visible:ring-primary/30 block h-11 w-full rounded-xl border border-black/10 bg-white px-3.5 text-base shadow-xs outline-none focus-visible:ring-2"
        />
      </div>
      <Button type="submit" className="w-full">
        Sign in
      </Button>
      <p role="status" aria-live="polite" className="text-ink-muted min-h-5 text-sm">
        {message}
      </p>
    </form>
  );
}
