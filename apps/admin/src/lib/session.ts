import "server-only";
import { createApiClient, type components } from "@shikshablox/api-client";
import { cookies, headers } from "next/headers";

/**
 * Backend-for-frontend session handling. Tokens never reach browser JavaScript: they live in
 * httpOnly, SameSite=Strict cookies set by this server, and every backend call is made from here.
 */
export const COOKIE = { access: "sb_at", refresh: "sb_rt", mfa: "sb_mfa" } as const;

export type TokenPair = components["schemas"]["TokenPair"];
export type Me = components["schemas"]["MeResponse"];

const API = process.env.API_INTERNAL_URL ?? "http://localhost:8080";

/** Secure (HTTPS-only) cookies in production; the local HTTP stack sets SB_SECURE_COOKIES=false. */
export const secureCookies = () =>
  process.env.NODE_ENV === "production" && process.env.SB_SECURE_COOKIES !== "false";

const baseCookie = () => ({
  httpOnly: true,
  sameSite: "strict" as const,
  secure: secureCookies(),
  path: "/",
});

/** Forwards the real client address so backend rate limits apply per person, not per server. */
async function forwardedHeaders(): Promise<Record<string, string>> {
  const h = await headers();
  const out: Record<string, string> = {};
  const xff = h.get("x-forwarded-for");
  if (xff) out["X-Forwarded-For"] = xff;
  const ua = h.get("user-agent");
  if (ua) out["User-Agent"] = ua.slice(0, 300);
  const rid = h.get("x-request-id");
  if (rid) out["X-Request-Id"] = rid;
  return out;
}

export async function api(accessToken?: string) {
  const extra = await forwardedHeaders();
  return createApiClient({
    baseUrl: API,
    headers: accessToken ? { ...extra, Authorization: `Bearer ${accessToken}` } : extra,
    cache: "no-store",
  });
}

export async function storeTokens(t: TokenPair) {
  const jar = await cookies();
  jar.set(COOKIE.access, t.accessToken!, {
    ...baseCookie(),
    expires: new Date(t.accessTokenExpiresAt!),
  });
  jar.set(COOKIE.refresh, t.refreshToken!, {
    ...baseCookie(),
    expires: new Date(t.refreshTokenExpiresAt!),
  });
  jar.delete(COOKIE.mfa);
}

export async function storeMfaChallenge(token: string) {
  (await cookies()).set(COOKIE.mfa, token, { ...baseCookie(), maxAge: 300 });
}

export async function clearSession() {
  const jar = await cookies();
  jar.delete(COOKIE.access);
  jar.delete(COOKIE.refresh);
  jar.delete(COOKIE.mfa);
}

/** The signed-in user, or null. The proxy refreshes expired access tokens before rendering. */
export async function currentUser(): Promise<{ me: Me; accessToken: string } | null> {
  const token = (await cookies()).get(COOKIE.access)?.value;
  if (!token) return null;
  const client = await api(token);
  const { data, response } = await client.GET("/api/auth/v1/me");
  if (!response.ok || !data) return null;
  return { me: data, accessToken: token };
}

/** Problem-detail message from a failed backend call, safe to show to the user. */
export function problemMessage(
  error: unknown,
  fallback = "Something went wrong. Please try again.",
) {
  if (error && typeof error === "object" && "detail" in error && typeof error.detail === "string") {
    return error.detail;
  }
  return fallback;
}
