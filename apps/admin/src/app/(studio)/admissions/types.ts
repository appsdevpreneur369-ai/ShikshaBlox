export type Lead = {
  id: string;
  enquiryId?: string | null;
  studentName?: string | null;
  guardianName: string;
  phone: string;
  email?: string | null;
  gradeSought?: string | null;
  academicYear: string;
  source: string;
  stage: string;
  lostReason?: string | null;
  priority: string;
  assignedTo?: string | null;
  assignedName?: string | null;
  nextFollowUpAt?: string | null;
  previousSchool?: string | null;
  notes?: string | null;
  createdAt: string;
  updatedAt: string;
};

export type Activity = {
  id: string;
  kind: string;
  body?: string | null;
  fromStage?: string | null;
  toStage?: string | null;
  actorName?: string | null;
  createdAt: string;
};

export type Counsellor = { id: string; name: string; email: string };
export type Seat = { grade: string; capacity: number; sortOrder: number; admitted: number; offered: number };
export type Funnel = {
  academicYear: string;
  byStage: Record<string, number>;
  bySource: Record<string, number>;
  total: number;
  admitted: number;
  lost: number;
  conversionPercent: number | null;
};

export const STAGES = ["NEW", "CONTACTED", "VISIT_SCHEDULED", "VISITED", "APPLIED", "OFFERED", "ADMITTED", "LOST"];
export const OPEN_STAGES = STAGES.filter((s) => s !== "ADMITTED" && s !== "LOST");

export const STAGE_LABEL: Record<string, string> = {
  NEW: "New",
  CONTACTED: "Contacted",
  VISIT_SCHEDULED: "Visit booked",
  VISITED: "Visited",
  APPLIED: "Applied",
  OFFERED: "Offered",
  ADMITTED: "Admitted",
  LOST: "Lost",
};

export const STAGE_TONE: Record<string, "blue" | "amber" | "purple" | "green" | "gray" | "red"> = {
  NEW: "blue",
  CONTACTED: "amber",
  VISIT_SCHEDULED: "purple",
  VISITED: "purple",
  APPLIED: "blue",
  OFFERED: "amber",
  ADMITTED: "green",
  LOST: "red",
};

export const SOURCE_LABEL: Record<string, string> = {
  WEBSITE: "Website",
  WALK_IN: "Walk-in",
  PHONE: "Phone",
  WHATSAPP: "WhatsApp",
  REFERRAL: "Referral",
  CAMPAIGN: "Campaign",
  OTHER: "Other",
};

export const KIND_LABEL: Record<string, string> = {
  CREATED: "Lead created",
  NOTE: "Note",
  CALL: "Call",
  VISIT: "Visit",
  MESSAGE: "Message",
  STAGE: "Stage changed",
  ASSIGNED: "Assigned",
  FOLLOW_UP: "Follow-up set",
};

/** "today" | "overdue" | "later" | null for a follow-up timestamp (IST calendar days). */
export function dueState(iso?: string | null): "overdue" | "today" | "later" | null {
  if (!iso) return null;
  const day = (d: Date) => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(d);
  const due = day(new Date(iso));
  const today = day(new Date());
  if (due < today) return "overdue";
  if (due === today) return "today";
  return "later";
}

export const fmtDay = (iso?: string | null) =>
  iso ? new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", timeZone: "Asia/Kolkata" }).format(new Date(iso)) : "—";

/** Value for <input type="date"> from an ISO instant, in IST. */
export const toDateInput = (iso?: string | null) =>
  iso ? new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date(iso)) : "";

/** 10:00 IST on the chosen day, as an ISO instant. */
export const fromDateInput = (v: string) => (v ? new Date(`${v}T10:00:00+05:30`).toISOString() : undefined);
