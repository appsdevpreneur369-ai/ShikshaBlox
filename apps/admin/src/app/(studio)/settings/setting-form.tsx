"use client";

import { useActionState } from "react";
import { saveSetting, type SaveState } from "./actions";

type Props = { settingKey: string; type: string; value: unknown; editable: boolean };

function display(type: string, value: unknown) {
  if (type === "STRING_LIST") return Array.isArray(value) ? value.join(", ") : "";
  if (type === "BOOLEAN") return value ? "On" : "Off";
  return String(value ?? "");
}

/** One setting: read-only text, or an inline form for editable keys. */
export function SettingForm({ settingKey, type, value, editable }: Props) {
  const [state, action, pending] = useActionState<SaveState, FormData>(saveSetting, {});
  if (!editable) {
    return <p className="text-sm font-semibold">{display(type, value)}</p>;
  }
  const inputId = `setting-${settingKey}`;
  return (
    <form action={action} className="flex flex-wrap items-center gap-2">
      <input type="hidden" name="key" value={settingKey} />
      <input type="hidden" name="type" value={type} />
      {type === "BOOLEAN" ? (
        <label htmlFor={inputId} className="inline-flex items-center gap-2 text-sm">
          <input
            id={inputId}
            type="checkbox"
            name="value"
            defaultChecked={Boolean(value)}
            className="accent-primary size-4"
          />
          Enabled
        </label>
      ) : (
        <>
          <label htmlFor={inputId} className="sr-only">
            {settingKey}
          </label>
          <input
            id={inputId}
            name="value"
            type={type === "INTEGER" ? "number" : "text"}
            defaultValue={display(type, value)}
            placeholder={type === "STRING_LIST" ? "Comma-separated" : undefined}
            className="focus-visible:ring-primary/30 h-9 min-w-0 flex-1 rounded-lg border border-black/10 px-3 text-sm outline-none focus-visible:ring-2"
          />
        </>
      )}
      <button
        disabled={pending}
        className="bg-primary text-on-primary h-9 rounded-full px-4 text-sm font-semibold transition hover:-translate-y-0.5 disabled:opacity-50"
      >
        Save
      </button>
      <span aria-live="polite" className="text-xs">
        {state.error ? (
          <span className="text-red-700">{state.error}</span>
        ) : state.saved ? (
          <span className="text-emerald-700">Saved</span>
        ) : null}
      </span>
    </form>
  );
}
