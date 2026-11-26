/**
 * Demo-request form: shared validation (browser + route handler) and the mapping to the platform's
 * public enquiry contract (name, phone, email, gradeSought, message, pageUrl, consent, website).
 */
export type DemoRequest = {
  name: string;
  school: string;
  role: string;
  phone: string;
  email: string;
  city: string;
  students: string;
  plan: string;
  message: string;
  consent: boolean;
  /** Honeypot: real visitors never see or fill this field. */
  website?: string;
};

export type FieldErrors = Partial<Record<keyof DemoRequest, string>>;

// Same rule as the backend (EnquiryService.PHONE), plus at least 8 digits.
const PHONE = /^[+0-9 -]{8,20}$/;
const digits = (v: string) => v.replace(/\D/g, "").length;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

export const STUDENT_BANDS = ["Under 200", "200–600", "600–1,500", "1,500–2,500", "2,500+"] as const;
export const ROLES = ["Principal", "Owner / Trustee / Director", "Administrator", "Accounts", "Teacher", "Other"] as const;

export function validateDemoRequest(input: Partial<DemoRequest>): FieldErrors {
  const e: FieldErrors = {};
  const t = (v: unknown) => (typeof v === "string" ? v.trim() : "");
  if (t(input.name).length < 2) e.name = "Please enter your name.";
  else if (t(input.name).length > 120) e.name = "Name is too long.";
  if (t(input.school).length < 2) e.school = "Please enter your school's name.";
  else if (t(input.school).length > 160) e.school = "School name is too long.";
  if (!PHONE.test(t(input.phone)) || digits(t(input.phone)) < 8) e.phone = "Enter a valid phone number.";
  if (t(input.email) && !EMAIL.test(t(input.email))) e.email = "Enter a valid email address.";
  if (t(input.city).length > 80) e.city = "City is too long.";
  if (t(input.message).length > 1200) e.message = "Please keep the message under 1,200 characters.";
  if (input.consent !== true) e.consent = "Please agree so we can contact you about your demo.";
  return e;
}

/** Builds the enquiry payload for the platform API (the message field carries the school details). */
export function toEnquiry(input: DemoRequest, pageUrl: string) {
  const t = (v: string | undefined) => (v ?? "").trim();
  const lines = [
    `School: ${t(input.school)}`,
    t(input.role) && `Role: ${t(input.role)}`,
    t(input.city) && `City: ${t(input.city)}`,
    t(input.students) && `Students: ${t(input.students)}`,
    t(input.plan) && `Interested in: ${t(input.plan)}`,
    t(input.message) && `Message: ${t(input.message)}`,
  ].filter(Boolean);
  return {
    name: t(input.name),
    phone: t(input.phone),
    email: t(input.email) || undefined,
    gradeSought: t(input.plan) ? `Plan: ${t(input.plan)}`.slice(0, 60) : undefined,
    message: lines.join("\n").slice(0, 2000),
    pageUrl: pageUrl.slice(0, 200),
    consent: input.consent === true,
    website: t(input.website),
  };
}
