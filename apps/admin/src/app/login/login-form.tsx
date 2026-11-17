"use client";

import { useActionState, useState } from "react";
import { CodeField, FormMessage, SubmitButton, TextField } from "@/components/fields";
import { passwordLogin, requestCode, verifyCode, type FormState } from "./actions";

type Mode = "password" | "code";

/** Sign in with a password, or with a one-time code sent by email. */
export function LoginForm({ initialError }: { initialError?: string }) {
  const [mode, setMode] = useState<Mode>("password");
  return (
    <div className="mt-8">
      <div
        role="tablist"
        aria-label="Sign-in method"
        className="bg-surface-muted grid grid-cols-2 gap-1 rounded-full p-1"
      >
        {(["password", "code"] as const).map((m) => (
          <button
            key={m}
            type="button"
            role="tab"
            aria-selected={mode === m}
            onClick={() => setMode(m)}
            className="aria-selected:text-primary rounded-full px-3 py-2 text-sm font-medium transition aria-selected:bg-white aria-selected:shadow-sm"
          >
            {m === "password" ? "Password" : "Email code"}
          </button>
        ))}
      </div>
      {mode === "password" ? <PasswordForm initialError={initialError} /> : <EmailCodeForm />}
    </div>
  );
}

function PasswordForm({ initialError }: { initialError?: string }) {
  const [state, action] = useActionState<FormState, FormData>(passwordLogin, {
    error: initialError,
  });
  return (
    <form action={action} className="mt-6 space-y-5">
      <TextField label="Email" name="email" type="email" autoComplete="username" required />
      <TextField
        label="Password"
        name="password"
        type="password"
        autoComplete="current-password"
        required
      />
      <SubmitButton>Sign in</SubmitButton>
      <FormMessage error={state.error} info={state.info} />
    </form>
  );
}

function EmailCodeForm() {
  const [email, setEmail] = useState("");
  const [sent, send] = useActionState<FormState, FormData>(requestCode, {});
  const [verified, verify] = useActionState<FormState, FormData>(verifyCode, {});
  const step = sent.step === "code" ? "code" : "email";

  if (step === "email") {
    return (
      <form action={send} className="mt-6 space-y-5">
        <TextField
          label="Email"
          name="email"
          type="email"
          autoComplete="username"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          hint="We'll email you a code that works for 10 minutes."
        />
        <SubmitButton>Email me a code</SubmitButton>
        <FormMessage error={sent.error} />
      </form>
    );
  }
  return (
    <form action={verify} className="mt-6 space-y-5">
      <input type="hidden" name="email" value={email} />
      <FormMessage info={verified.error ? undefined : sent.info} error={verified.error} />
      <CodeField />
      <SubmitButton>Sign in</SubmitButton>
    </form>
  );
}
