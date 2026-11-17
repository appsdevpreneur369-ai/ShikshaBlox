"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { COOKIE, api, storeTokens } from "@/lib/session";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Makes another school active. The backend re-checks membership; nothing is trusted from here. */
export async function switchSchool(form: FormData) {
  const organizationId = String(form.get("organizationId") ?? "");
  const jar = await cookies();
  const access = jar.get(COOKIE.access)?.value;
  const refreshToken = jar.get(COOKIE.refresh)?.value;
  if (!UUID.test(organizationId) || !access || !refreshToken) redirect("/login");
  const client = await api(access);
  const { data } = await client.POST("/api/auth/v1/switch-organization", {
    body: { organizationId, refreshToken },
  });
  if (data) await storeTokens(data);
  redirect("/dashboard");
}
