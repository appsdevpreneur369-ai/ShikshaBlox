"use server";

import { revalidatePath } from "next/cache";
import { cookies } from "next/headers";
import { COOKIE, api, problemMessage } from "@/lib/session";

export type SaveState = { saved?: boolean; error?: string };

const KEY = /^[a-z][a-z0-9_.]{2,99}$/;

export async function saveSetting(_: SaveState, form: FormData): Promise<SaveState> {
  const key = String(form.get("key") ?? "");
  const type = String(form.get("type") ?? "");
  const raw = form.get("value");
  if (!KEY.test(key)) return { error: "Unknown setting." };

  let value: unknown;
  switch (type) {
    case "BOOLEAN":
      value = raw === "on";
      break;
    case "INTEGER":
      value = Number(raw);
      if (!Number.isInteger(value)) return { error: "Enter a whole number." };
      break;
    case "STRING_LIST":
      value = String(raw ?? "")
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
      break;
    default:
      value = String(raw ?? "").trim();
  }

  const token = (await cookies()).get(COOKIE.access)?.value;
  if (!token) return { error: "Your session has expired. Please sign in again." };
  const client = await api(token);
  const { error } = await client.PUT("/api/admin/v1/settings/{key}", {
    params: { path: { key } },
    body: { value: value as never },
  });
  if (error) return { error: problemMessage(error) };
  revalidatePath("/settings");
  return { saved: true };
}
