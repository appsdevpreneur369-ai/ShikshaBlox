"use client";

import { useCallback, useEffect, useState } from "react";
import { Copy, KeyRound } from "lucide-react";
import { sb, fmtDate } from "@/lib/studio";
import { Badge, Btn, useAction } from "@/components/kit";

type Access = {
  guardianId: string;
  name: string;
  phone?: string | null;
  status: string;
  lastLoginAt?: string | null;
  inviteExpiresAt?: string | null;
};
type Invite = { link: string; expiresAt: string };

const STATUS: Record<string, [string, "gray" | "amber" | "green" | "red"]> = {
  NONE: ["No access", "gray"],
  INVITED: ["Invited", "amber"],
  ACTIVE: ["Active", "green"],
  DISABLED: ["Removed", "red"],
};

/** Parent portal access for each of the student's guardians. */
export function PortalAccess({ studentId }: { studentId: string }) {
  const [rows, setRows] = useState<Access[] | null>(null);
  const [links, setLinks] = useState<Record<string, Invite>>({});
  const [copied, setCopied] = useState<string | null>(null);
  const { busy, run } = useAction();

  const load = useCallback(async () => {
    try {
      setRows(await sb<Access[]>(`portal/students/${studentId}/access`));
    } catch {
      setRows([]);
    }
  }, [studentId]);
  useEffect(() => {
    const t = setTimeout(() => void load(), 0);
    return () => clearTimeout(t);
  }, [load]);

  if (!rows || rows.length === 0) return null;

  async function invite(a: Access) {
    if (
      a.status === "ACTIVE" &&
      !confirm(
        `${a.name} already uses the portal. A new link resets their PIN and signs them out. Continue?`,
      )
    )
      return;
    const r = await run(
      "inv" + a.guardianId,
      () => sb<Invite>(`portal/guardians/${a.guardianId}/invite`, { method: "POST" }),
      "Invitation link created",
    );
    if (r) {
      setLinks({ ...links, [a.guardianId]: r });
      void load();
    }
  }
  async function disable(a: Access) {
    if (!confirm(`Remove ${a.name}'s portal access and sign them out?`)) return;
    if (
      await run(
        "dis" + a.guardianId,
        () => sb(`portal/guardians/${a.guardianId}/disable`, { method: "POST" }),
        "Access removed",
      )
    )
      void load();
  }

  return (
    <div className="mt-5 rounded-xl bg-black/[0.03] p-3">
      <p className="mb-2 flex items-center gap-1.5 text-sm font-medium">
        <KeyRound className="size-4" aria-hidden /> Parent portal
      </p>
      <ul className="space-y-2">
        {rows.map((a) => {
          const [label, tone] = STATUS[a.status] ?? ["—", "gray"];
          const link = links[a.guardianId];
          return (
            <li key={a.guardianId} className="text-sm">
              <div className="flex flex-wrap items-center gap-2">
                <span className="min-w-0 flex-1">
                  {a.name}
                  <span className="text-ink-muted block text-xs">
                    {a.status === "ACTIVE" && a.lastLoginAt
                      ? `Last signed in ${fmtDate(a.lastLoginAt)}`
                      : a.status === "INVITED" && a.inviteExpiresAt
                        ? `Link valid until ${fmtDate(a.inviteExpiresAt)}`
                        : (a.phone ?? "No mobile number")}
                  </span>
                </span>
                <Badge tone={tone}>{label}</Badge>
                <Btn
                  size="sm"
                  busy={busy === "inv" + a.guardianId}
                  disabled={!a.phone}
                  onClick={() => void invite(a)}
                >
                  {a.status === "NONE"
                    ? "Invite"
                    : a.status === "ACTIVE"
                      ? "Reset PIN"
                      : "New link"}
                </Btn>
                {(a.status === "ACTIVE" || a.status === "INVITED") && (
                  <Btn
                    size="sm"
                    variant="ghost"
                    busy={busy === "dis" + a.guardianId}
                    onClick={() => void disable(a)}
                  >
                    Remove
                  </Btn>
                )}
              </div>
              {link && (
                <div className="mt-1.5 flex items-center gap-2 rounded-lg bg-white p-2 ring-1 ring-black/10">
                  <code className="min-w-0 flex-1 truncate text-xs">{link.link}</code>
                  <Btn
                    size="sm"
                    variant="ghost"
                    onClick={() => {
                      void navigator.clipboard.writeText(link.link);
                      setCopied(a.guardianId);
                    }}
                    aria-label={`Copy ${a.name}'s invitation link`}
                  >
                    <Copy className="size-3.5" aria-hidden />{" "}
                    {copied === a.guardianId ? "Copied" : "Copy"}
                  </Btn>
                </div>
              )}
            </li>
          );
        })}
      </ul>
      <p className="text-ink-muted mt-2 text-xs">
        Send the personal link to the parent (WhatsApp, SMS or email). It works once, for 14 days;
        they choose a PIN and then sign in at your website&apos;s /parent page.
      </p>
    </div>
  );
}
