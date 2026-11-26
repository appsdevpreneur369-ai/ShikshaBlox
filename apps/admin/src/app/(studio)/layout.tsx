import { redirect } from "next/navigation";
import type { ReactNode } from "react";
import { LogOut } from "lucide-react";
import { currentUser } from "@/lib/session";
import { StudioNav, type NavItem } from "@/components/studio-nav";
import { logout } from "../login/actions";
import { ToastProvider } from "@/components/kit";

function navItems(permissions: string[], hasSchool: boolean): NavItem[] {
  const items: NavItem[] = [{ href: "/dashboard", label: "Dashboard" }];
  if (hasSchool) {
    items.push({ href: "/pages", label: "Pages" });
    items.push({ href: "/content", label: "Content" });
    items.push({ href: "/media", label: "Media" });
    if (permissions.includes("enquiry:manage"))
      items.push({ href: "/enquiries", label: "Enquiries" });
    if (permissions.includes("admissions:manage"))
      items.push({ href: "/admissions", label: "Admissions" });
    if (permissions.includes("frontoffice:manage"))
      items.push({ href: "/front-office", label: "Front office" });
    if (permissions.includes("comms:manage")) items.push({ href: "/messages", label: "Messages" });
    if (permissions.includes("engagement:manage"))
      items.push({ href: "/parents", label: "Parents" });
    if (permissions.includes("compliance:manage"))
      items.push({ href: "/compliance", label: "Compliance" });
    if (permissions.includes("school:manage"))
      items.push({ href: "/design", label: "Design & profile" });
    items.push({ href: "/plan", label: "Plan & usage" });
    items.push({ href: "/settings", label: "Settings" });
    if (permissions.includes("audit:view")) items.push({ href: "/audit", label: "Audit log" });
  }
  return items;
}

export const dynamic = "force-dynamic";

const ROLE_LABEL: Record<string, string> = {
  PLATFORM_SUPER_ADMIN: "Platform admin",
  SCHOOL_ADMIN: "School admin",
  CONTENT_APPROVER: "Approver",
  ADMISSIONS_COUNSELLOR: "Counsellor",
  FRONT_OFFICE: "Front office",
  CONTENT_EDITOR: "Editor",
  VIEWER: "Viewer",
};

function roleLabel(role: string) {
  return ROLE_LABEL[role] ?? role;
}

export default async function StudioLayout({ children }: { children: ReactNode }) {
  const session = await currentUser();
  if (!session) redirect("/login");
  const { me } = session;
  const initials = (me.displayName ?? "?")
    .split(/\s+/)
    .map((p) => p[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();

  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-10 border-b border-black/5 bg-white/80 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4">
          <div className="flex items-center gap-3">
            <span className="bg-primary text-on-primary grid size-9 place-items-center rounded-xl text-sm font-bold">
              SB
            </span>
            <div className="leading-tight">
              <p className="text-sm font-semibold">ShikshaBlox Studio</p>
              <p className="text-ink-muted text-xs">{me.activeOrganization?.name ?? "Platform"}</p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <div className="hidden text-right leading-tight sm:block">
              <p className="text-sm font-medium">{me.displayName}</p>
              <p className="text-ink-muted text-xs">{me.roles?.map(roleLabel).join(", ")}</p>
            </div>
            <span
              aria-hidden
              className="grid size-9 place-items-center rounded-full bg-indigo-100 text-sm font-semibold text-indigo-800"
            >
              {initials}
            </span>
            <form action={logout}>
              <button
                type="submit"
                className="text-ink-muted hover:text-ink inline-flex h-9 items-center gap-1.5 rounded-full px-3 text-sm font-medium transition hover:bg-black/5"
              >
                <LogOut className="size-4" aria-hidden />
                <span>Sign out</span>
              </button>
            </form>
          </div>
        </div>
      </header>
      <StudioNav items={navItems(me.permissions ?? [], Boolean(me.activeOrganization))} />
      <main className="mx-auto max-w-6xl px-4 py-8">
        <ToastProvider>{children}</ToastProvider>
      </main>
    </div>
  );
}
