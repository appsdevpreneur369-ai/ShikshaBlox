import type { Metadata } from "next";
import { LoginForm } from "./login-form";

export const metadata: Metadata = { title: "Sign in" };

export default function LoginPage() {
  return (
    <main className="grid min-h-dvh place-items-center px-4 py-12">
      <div className="bg-surface w-full max-w-md rounded-3xl p-8 shadow-xl ring-1 shadow-indigo-950/5 ring-black/5 sm:p-10">
        <p className="text-primary text-sm font-semibold tracking-widest uppercase">ShikshaBlox</p>
        <h1 className="mt-2 text-2xl font-bold">Sign in to Studio</h1>
        <p className="text-ink-muted mt-1 text-sm">Manage your school website and content.</p>
        <LoginForm />
      </div>
    </main>
  );
}
