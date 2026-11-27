export type Status = "P" | "A" | "L" | "H" | "LV";

export const STATUS: Record<Status, { short: string; label: string; on: string }> = {
  P: { short: "P", label: "Present", on: "bg-emerald-600 text-white" },
  A: { short: "A", label: "Absent", on: "bg-red-600 text-white" },
  L: { short: "L", label: "Late", on: "bg-amber-500 text-white" },
  H: { short: "½", label: "Half day", on: "bg-indigo-600 text-white" },
  LV: { short: "LV", label: "On leave", on: "bg-gray-700 text-white" },
};

export type Settings = {
  mode: "DAILY" | "PERIOD";
  periodsPerDay: number;
  alertAbsent: boolean;
  alertLate: boolean;
  minPercent: number;
  concernPercent: number;
  concernStreak: number;
  reasonCodes: string[];
  periodsAllowed: boolean;
  reasonsAllowed: boolean;
  concernsAllowed: boolean;
  messagingAvailable: boolean;
};

export type SectionDay = {
  id: string;
  label: string;
  classTeacher?: string | null;
  students: number;
  marked: boolean;
  present: number;
  absent: number;
  late: number;
  onLeave: number;
};

export type Overview = {
  date: string;
  year: string;
  canManage: boolean;
  settings: Settings;
  sections: SectionDay[];
  pendingLeave: number;
  pendingCorrections: number;
};

export type SheetRow = {
  studentId: string;
  name: string;
  rollNo?: number | null;
  admissionNo: string;
  status?: Status | null;
  reason?: string | null;
  note?: string | null;
  leave?: "APPROVED" | "PENDING" | null;
  alert?: string | null;
  correctionPending: boolean;
};

export type Sheet = {
  sectionId: string;
  label: string;
  date: string;
  period: number;
  subject?: string | null;
  marked: boolean;
  markedBy?: string | null;
  markedAt?: string | null;
  editMode: "DIRECT" | "CORRECTION" | "READ_ONLY";
  rows: SheetRow[];
};

export type MarkInput = {
  studentId: string;
  status: Status;
  reason?: string | null;
  note?: string | null;
};

export type SheetInput = {
  sectionId: string;
  date: string;
  period: number;
  subject?: string | null;
  marks: MarkInput[];
  correctionReason?: string | null;
};

export type SaveResult = {
  sheet: Sheet;
  saved: number;
  corrections: number;
  alerts: number;
  message: string;
};

export type Leave = {
  id: string;
  studentId: string;
  studentName: string;
  classLabel?: string | null;
  fromDate: string;
  toDate: string;
  days: number;
  reason: string;
  source: "PORTAL" | "STAFF";
  status: "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";
  requestedBy?: string | null;
  decidedBy?: string | null;
  decidedAt?: string | null;
  decisionNote?: string | null;
  createdAt: string;
};

export type Correction = {
  id: string;
  studentId: string;
  studentName: string;
  classLabel: string;
  date: string;
  period: number;
  fromStatus?: Status | null;
  toStatus: Status;
  toReason?: string | null;
  reason: string;
  status: string;
  requestedBy?: string | null;
  decidedBy?: string | null;
  createdAt: string;
};

export type RegisterRow = {
  studentId: string;
  name: string;
  rollNo?: number | null;
  marks: Record<string, Status>;
  present: number;
  absent: number;
  late: number;
  halfDays: number;
  leave: number;
  percent?: number | null;
  yearPercent?: number | null;
  belowMinimum: boolean;
};

export type Register = {
  sectionId: string;
  label: string;
  month: string;
  days: string[];
  minPercent: number;
  rows: RegisterRow[];
};

export type Concern = {
  studentId: string;
  name: string;
  classLabel: string;
  percent?: number | null;
  streak: number;
  lastAbsent?: string | null;
  why: string;
};

export type Teacher = { id: string; name: string; email: string };
export type Assignment = {
  sectionId: string;
  label: string;
  classTeacher?: string | null;
  teachers: Teacher[];
};
export type Teachers = { staff: Teacher[]; sections: Assignment[] };

/** Today in India as yyyy-mm-dd. */
export const todayIst = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date());

export const dayLabel = (d: string) =>
  new Intl.DateTimeFormat("en-IN", { weekday: "short", day: "numeric", month: "short" }).format(
    new Date(`${d}T00:00:00`),
  );
