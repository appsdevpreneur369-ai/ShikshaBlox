export type Component = { key: string; label: string; max: number };
export type Band = { min: number; grade: string; point: number };

export type Settings = {
  board: "CBSE" | "ICSE" | "STATE" | "OTHER";
  passPercent: number;
  bands: Band[];
  coGrades: string[];
  principalName?: string | null;
  showRank: boolean;
  timetable: boolean;
  hallTickets: boolean;
  moderation: boolean;
  analysis: boolean;
  canManage: boolean;
};

export type Exam = {
  id: string;
  name: string;
  term?: string | null;
  grades: string[];
  components: Component[];
  maxTotal: number;
  weight: number;
  startDate?: string | null;
  endDate?: string | null;
  status: "DRAFT" | "MARKS_OPEN" | "PUBLISHED";
  publishedAt?: string | null;
};

export type Overview = { year: string; settings: Settings; grades: string[]; exams: Exam[] };

export type Subject = {
  id: string;
  grade: string;
  name: string;
  kind: "SCHOLASTIC" | "CO_SCHOLASTIC";
  sortOrder: number;
};

export type Task = {
  sectionId: string;
  section: string;
  subjectId: string;
  subject: string;
  kind: string;
  students: number;
  entered: number;
  status: "OPEN" | "SUBMITTED" | "APPROVED";
};

export type Row = {
  studentId: string;
  name: string;
  rollNo?: number | null;
  marks: Record<string, number>;
  grade?: string | null;
  code?: "AB" | "ML" | null;
  total?: number | null;
  percent?: number | null;
  resultGrade?: string | null;
};

export type Sheet = {
  examId: string;
  exam: string;
  examStatus: string;
  sectionId: string;
  section: string;
  subjectId: string;
  subject: string;
  kind: string;
  components: Component[];
  coGrades: string[];
  entryStatus: "OPEN" | "SUBMITTED" | "APPROVED";
  note?: string | null;
  editable: boolean;
  moderation: boolean;
  canManage: boolean;
  rows: Row[];
};

export type Remark = {
  studentId: string;
  name: string;
  rollNo?: number | null;
  remark?: string | null;
};

export type Paper = {
  subjectId: string;
  subject: string;
  grade: string;
  kind: string;
  included: boolean;
  date?: string | null;
  startTime?: string | null;
  durationMin?: number | null;
};

export type Staff = { id: string; name: string };
export type Teachers = {
  staff: Staff[];
  sections: {
    sectionId: string;
    label: string;
    grade: string;
    subjects: { subjectId: string; subject: string; teachers: Staff[] }[];
  }[];
};

export type Analysis = {
  sectionId: string;
  section: string;
  students: number;
  classAverage?: number | null;
  subjects: {
    subject: string;
    average?: number | null;
    highest?: number | null;
    lowest?: number | null;
    passed: number;
    withResult: number;
  }[];
  toppers: { name: string; percent: number }[];
};

export const STATUS_LABEL: Record<Exam["status"], string> = {
  DRAFT: "Draft",
  MARKS_OPEN: "Marks open",
  PUBLISHED: "Published",
};
export const ENTRY_LABEL: Record<Task["status"], string> = {
  OPEN: "Entering",
  SUBMITTED: "Waiting for approval",
  APPROVED: "Approved",
};

export const fmtDay = (d?: string | null) =>
  d
    ? new Intl.DateTimeFormat("en-IN", { weekday: "short", day: "numeric", month: "short" }).format(
        new Date(`${d}T00:00:00`),
      )
    : "—";
