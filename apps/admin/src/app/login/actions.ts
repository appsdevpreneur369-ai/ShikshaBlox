"use server";

import { redirect } from "next/navigation";
import { cookies } from "next/headers";
import {
  COOKIE,
  api,
  clearSession,
  problemMessage,
  storeMfaChallenge,
  storeTokens,
  type TokenPair,
} from "@/lib/session";

/** Result of a form action, rendered by the client form. */
export type FormState = { error?: string; info?: string; step?: "code" };

type LoginResponse = { status?: string; tokens?: TokenPair; mfaToken?: string };

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

/** Shared continuation after a first factor: finish, or go to the MFA step. */
async function continueLogin(data: LoginResponse | undefined): Promise<never> {
  if (data?.status === "AUTHENTICATED" && data.tokens) {
    await storeTokens(data.tokens);
    redirect("/dashboard");
  }
  if (data?.mfaToken && data.status === "MFA_REQUIRED") {
    await storeMfaChallenge(data.mfaToken);
    redirect("/login/mfa");
  }
  if (data?.mfaToken && data.status === "MFA_ENROLLMENT_REQUIRED") {
    await storeMfaChallenge(data.mfaToken);
    redirect("/login/mfa-setup");
  }
  redirect("/login?error=unexpected");
}

export async function passwordLogin(_: FormState, form: FormData): Promise<FormState> {
  const email = String(form.get("email") ?? "").trim();
  const password = String(form.get("password") ?? "");
  if (!EMAIL.test(email) || !password) {
    return { error: "Enter your email address and password." };
  }
  const client = await api();
  const { data, error } = await client.POST("/api/auth/v1/login", { body: { email, password } });
  if (error) return { error: problemMessage(error) };
  return continueLogin(data);
}

export async function requestCode(_: FormState, form: FormData): Promise<FormState> {
  const email = String(form.get("email") ?? "").trim();
  if (!EMAIL.test(email)) return { error: "Enter a valid email address." };
  const client = await api();
  const { error } = await client.POST("/api/auth/v1/otp/request", { body: { email } });
  if (error) return { error: problemMessage(error) };
  return {
    step: "code",
    info: "If that address has a Studio account, we have emailed it a 6-digit code.",
  };
}

export async function verifyCode(_: FormState, form: FormData): Promise<FormState> {
  const email = String(form.get("email") ?? "").trim();
  const code = String(form.get("code") ?? "").replace(/\s/g, "");
  if (!/^\d{6}$/.test(code)) return { step: "code", error: "Enter the 6-digit code." };
  const client = await api();
  const { data, error } = await client.POST("/api/auth/v1/otp/verify", { body: { email, code } });
  if (error) return { step: "code", error: problemMessage(error) };
  return continueLogin(data);
}

async function mfaToken() {
  const token = (await cookies()).get(COOKIE.mfa)?.value;
  if (!token) redirect("/login?error=expired");
  return token;
}

export async function verifyMfa(_: FormState, form: FormData): Promise<FormState> {
  const code = String(form.get("code") ?? "").replace(/\s/g, "");
  if (!/^\d{6}$/.test(code)) return { error: "Enter the 6-digit code from your app." };
  const client = await api();
  const { data, error } = await client.POST("/api/auth/v1/mfa/verify", {
    body: { mfaToken: await mfaToken(), code },
  });
  if (error) return { error: problemMessage(error) };
  return continueLogin(data);
}

export async function confirmMfaSetup(_: FormState, form: FormData): Promise<FormState> {
  const code = String(form.get("code") ?? "").replace(/\s/g, "");
  if (!/^\d{6}$/.test(code)) return { error: "Enter the 6-digit code from your app." };
  const client = await api();
  const { data, error } = await client.POST("/api/auth/v1/mfa/enroll/confirm", {
    body: { mfaToken: await mfaToken(), code },
  });
  if (error) return { error: problemMessage(error) };
  return continueLogin(data);
}

export async function logout() {
  const refreshToken = (await cookies()).get(COOKIE.refresh)?.value;
  if (refreshToken) {
    const client = await api();
    await client.POST("/api/auth/v1/logout", { body: { refreshToken } });
  }
  await clearSession();
  redirect("/login");
}
