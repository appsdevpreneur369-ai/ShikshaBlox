"use client";

import { useActionState } from "react";
import { CodeField, FormMessage, SubmitButton } from "@/components/fields";
import { confirmMfaSetup, verifyMfa, type FormState } from "../actions";

export function MfaForm({ setup = false }: { setup?: boolean }) {
  const [state, action] = useActionState<FormState, FormData>(
    setup ? confirmMfaSetup : verifyMfa,
    {},
  );
  return (
    <form action={action} className="mt-6 space-y-5">
      <CodeField label="Authenticator code" />
      <SubmitButton>{setup ? "Turn on and sign in" : "Verify"}</SubmitButton>
      <FormMessage error={state.error} />
      <p className="text-center text-sm">
        <a href="/login" className="text-primary font-medium hover:underline">
          Use a different account
        </a>
      </p>
    </form>
  );
}
