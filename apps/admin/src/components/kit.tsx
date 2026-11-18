"use client";

import { cn } from "@shikshablox/ui";
import { Loader2 } from "lucide-react";
import { createContext, useCallback, useContext, useState, type ButtonHTMLAttributes, type ReactNode } from "react";

/** Small Studio UI kit: buttons, badges, cards, headers and toasts. */

type Variant = "primary" | "secondary" | "ghost" | "danger" | "success";
const VARIANTS: Record<Variant, string> = {
  primary: "bg-primary text-on-primary hover:brightness-110 shadow-sm",
  secondary: "bg-white text-ink ring-1 ring-black/10 hover:bg-black/[0.03]",
  ghost: "text-ink-muted hover:text-ink hover:bg-black/5",
  danger: "bg-white text-red-700 ring-1 ring-red-200 hover:bg-red-50",
  success: "bg-emerald-600 text-white hover:bg-emerald-700 shadow-sm",
};

export function Btn({
  variant = "secondary",
  busy,
  className,
  children,
  size = "md",
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; busy?: boolean; size?: "sm" | "md" }) {
  return (
    <button
      type="button"
      {...props}
      disabled={props.disabled || busy}
      className={cn(
        "inline-flex items-center justify-center gap-1.5 rounded-full font-medium transition disabled:cursor-not-allowed disabled:opacity-50 focus-visible:ring-2 focus-visible:ring-primary/40 focus-visible:outline-none",
        size === "sm" ? "h-8 px-3 text-xs" : "h-10 px-4 text-sm",
        VARIANTS[variant],
        className,
      )}
    >
      {busy && <Loader2 className="size-4 animate-spin" aria-hidden />}
      {children}
    </button>
  );
}

const TONES = {
  green: "bg-emerald-50 text-emerald-700 ring-emerald-600/20",
  amber: "bg-amber-50 text-amber-800 ring-amber-600/20",
  blue: "bg-indigo-50 text-indigo-700 ring-indigo-600/20",
  gray: "bg-gray-50 text-gray-600 ring-gray-500/20",
  red: "bg-red-50 text-red-700 ring-red-600/20",
  purple: "bg-purple-50 text-purple-700 ring-purple-600/20",
} as const;

export function Badge({ tone = "gray", children }: { tone?: keyof typeof TONES; children: ReactNode }) {
  return (
    <span className={cn("inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset", TONES[tone])}>
      {children}
    </span>
  );
}

export const STATUS_TONE: Record<string, keyof typeof TONES> = {
  PUBLISHED: "green",
  DRAFT: "gray",
  IN_REVIEW: "amber",
  SCHEDULED: "purple",
  APPROVED: "blue",
  NEW: "blue",
  CONTACTED: "amber",
  ENROLLED: "green",
  CLOSED: "gray",
  SPAM: "red",
};
export const STATUS_LABEL: Record<string, string> = {
  PUBLISHED: "Published",
  DRAFT: "Draft",
  IN_REVIEW: "Waiting for approval",
  SCHEDULED: "Scheduled",
  NEW: "New",
  CONTACTED: "Contacted",
  ENROLLED: "Enrolled",
  CLOSED: "Closed",
  SPAM: "Spam",
};

export function StatusBadge({ status }: { status: string }) {
  return <Badge tone={STATUS_TONE[status] ?? "gray"}>{STATUS_LABEL[status] ?? status}</Badge>;
}

export function Card({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={cn("rounded-2xl bg-white p-5 shadow-sm ring-1 ring-black/5", className)}>{children}</div>;
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
        {subtitle && <p className="text-ink-muted mt-1 text-sm">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="text-ink-muted rounded-2xl border-2 border-dashed border-black/10 p-10 text-center text-sm">{children}</div>;
}

export function Loading() {
  return (
    <div className="text-ink-muted flex items-center gap-2 p-10 text-sm">
      <Loader2 className="size-4 animate-spin" aria-hidden /> Loading…
    </div>
  );
}

export const inputCls =
  "block w-full rounded-xl border border-black/10 bg-white px-3 py-2 text-sm shadow-xs outline-none transition focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-primary/30";

export function Field({ label, hint, children }: { label: string; hint?: ReactNode; children: ReactNode }) {
  return (
    <label className="block space-y-1.5">
      <span className="text-sm font-medium">{label}</span>
      {children}
      {hint && <span className="text-ink-muted block text-xs">{hint}</span>}
    </label>
  );
}

// ------------------------------------------------------------------ toasts

type Toast = { id: number; text: string; tone: "ok" | "error" };
const ToastCtx = createContext<(text: string, tone?: "ok" | "error") => void>(() => {});

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const push = useCallback((text: string, tone: "ok" | "error" = "ok") => {
    const id = Date.now() + Math.random();
    setToasts((t) => [...t, { id, text, tone }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 4500);
  }, []);
  return (
    <ToastCtx.Provider value={push}>
      {children}
      <div aria-live="polite" className="fixed right-4 bottom-4 z-50 flex flex-col gap-2">
        {toasts.map((t) => (
          <div
            key={t.id}
            className={cn(
              "rounded-xl px-4 py-3 text-sm font-medium shadow-lg",
              t.tone === "ok" ? "bg-gray-900 text-white" : "bg-red-600 text-white",
            )}
          >
            {t.text}
          </div>
        ))}
      </div>
    </ToastCtx.Provider>
  );
}

export const useToast = () => useContext(ToastCtx);

/** Runs an async action with busy state and toasts on success/failure. */
export function useAction() {
  const toast = useToast();
  const [busy, setBusy] = useState<string | null>(null);
  const run = useCallback(
    async <T,>(key: string, fn: () => Promise<T>, ok?: string): Promise<T | undefined> => {
      setBusy(key);
      try {
        const r = await fn();
        if (ok) toast(ok);
        return r;
      } catch (e) {
        toast(e instanceof Error ? e.message : "Something went wrong.", "error");
        return undefined;
      } finally {
        setBusy(null);
      }
    },
    [toast],
  );
  return { busy, run };
}
