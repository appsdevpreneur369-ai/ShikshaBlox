export type Section = {
  id: string;
  academicYear: string;
  grade: string;
  section: string;
  label: string;
  classTeacher?: string | null;
  gradeOrder: number;
  students: number;
};
export type StudentRow = {
  id: string;
  admissionNo: string;
  name: string;
  status: string;
  dateOfBirth?: string | null;
  gender?: string | null;
  sectionId?: string | null;
  sectionLabel?: string | null;
  rollNo?: number | null;
  guardianName?: string | null;
  guardianPhone?: string | null;
  medicalAlert: boolean;
  custodyNote: boolean;
};
export type Student = {
  id: string;
  admissionNo: string;
  name: string;
  dateOfBirth?: string | null;
  gender?: string | null;
  bloodGroup?: string | null;
  status: string;
  admissionDate?: string | null;
  leavingDate?: string | null;
  leavingReason?: string | null;
  previousSchool?: string | null;
  address?: string | null;
  house?: string | null;
  medicalAlerts?: string | null;
  custodyNote?: string | null;
  apaarId?: string | null;
  apaarStatus: string;
  apaarNote?: string | null;
  custom: Record<string, unknown>;
  leadId?: string | null;
};
export type Enrolment = {
  id: string;
  academicYear: string;
  sectionId: string;
  sectionLabel: string;
  rollNo?: number | null;
  outcome?: string | null;
};
export type GuardianLink = {
  guardianId: string;
  name: string;
  phone?: string | null;
  email?: string | null;
  occupation?: string | null;
  language: string;
  relation: string;
  primary: boolean;
  canPickup: boolean;
  emergency: boolean;
  siblings: string[];
};
export type Field = {
  id: string;
  key: string;
  label: string;
  type: string;
  options: string[];
  sortOrder: number;
};
export type Detail = {
  student: Student;
  enrolments: Enrolment[];
  guardians: GuardianLink[];
  documents: {
    id: string;
    title: string;
    filename: string;
    sizeBytes: number;
    createdAt: string;
  }[];
  fields: Field[];
};
export type Settings = { currentYear: string; admissionPrefix: string; nextAdmissionNo: number };

export const GENDER: Record<string, string> = { FEMALE: "Girl", MALE: "Boy", OTHER: "Other" };
export const RELATION: Record<string, string> = {
  MOTHER: "Mother",
  FATHER: "Father",
  GUARDIAN: "Guardian",
  GRANDPARENT: "Grandparent",
  OTHER: "Other",
};
export const APAAR: Record<string, string> = {
  NOT_STARTED: "Not started",
  CONSENT_PENDING: "Consent pending",
  CONSENT_GIVEN: "Consent given",
  CONSENT_REFUSED: "Consent refused",
  GENERATED: "APAAR ID generated",
  EXCEPTION: "Exception",
};
export const STATUS_LABEL: Record<string, string> = {
  ENROLLED: "Enrolled",
  WITHDRAWN: "Withdrawn",
  ALUMNI: "Alumni",
};
export const OUTCOME: Record<string, string> = {
  PROMOTED: "Promoted",
  RETAINED: "Retained",
  LEFT: "Left",
  GRADUATED: "Graduated",
};
export const fmtDay = (d?: string | null) =>
  d
    ? new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" }).format(
        new Date(`${d}T00:00:00`),
      )
    : "—";
export const today = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata" }).format(new Date());
