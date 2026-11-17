import type { Metadata } from "next";
import { AuthCard } from "@/components/auth-card";
import { LoginForm } from "./login-form";

export const metadata: Metadata = { title: "Sign in" };

const ERRORS: Record<string, string> = {
  expired: "That sign-in step expired. Please start again.",
  unexpected: "Something went wrong. Please try again.",
};

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>;
}) {
  const { error } = await searchParams;
  return (
    <AuthCard title="Sign in to Studio" subtitle="Manage your school website and content.">
      <LoginForm initialError={error ? ERRORS[error] : undefined} />
    </AuthCard>
  );
}
