export type Settings = {
  signatoryName?: string | null;
  signatoryTitle: string;
  schoolAddress?: string | null;
  affiliationNo?: string | null;
  schoolCode?: string | null;
  requireApproval: boolean;
  noDuesItems: string[];
  customTemplates: boolean;
  approvalFeature: boolean;
  parentRequests: boolean;
  canApprove: boolean;
};

export type Template = {
  id: string;
  kind: "TC" | "BONAFIDE" | "CHARACTER" | "STUDY" | "CUSTOM";
  name: string;
  title: string;
  body: string;
  prefix: string;
  nextNumber: number;
  parentRequestable: boolean;
  active: boolean;
};

export type Field = { key: string; label: string; value: string };

export type Draft = {
  templateId: string;
  kind: Template["kind"];
  title: string;
  studentId: string;
  studentName: string;
  classLabel: string;
  fields: Field[];
  body: string;
  noDuesItems: string[];
  problems: string[];
  willNeedApproval: boolean;
};

export type Certificate = {
  id: string;
  templateId: string;
  kind: Template["kind"];
  title: string;
  number?: string | null;
  verifyCode?: string | null;
  status: "REQUESTED" | "PENDING_APPROVAL" | "ISSUED" | "CANCELLED" | "REJECTED";
  studentId: string;
  studentName: string;
  admissionNo: string;
  classLabel?: string | null;
  purpose?: string | null;
  fields: Field[];
  body?: string | null;
  duplicateOf?: string | null;
  requestedBy?: string | null;
  createdBy?: string | null;
  approvedBy?: string | null;
  issuedBy?: string | null;
  issuedAt?: string | null;
  statusNote?: string | null;
  createdAt: string;
  verifyUrl?: string | null;
};

export type StudentHit = {
  id: string;
  name: string;
  admissionNo: string;
  classLabel?: string | null;
  status: string;
};

export const STATUS = {
  REQUESTED: { label: "Requested by parent", tone: "blue" },
  PENDING_APPROVAL: { label: "Waiting for approval", tone: "amber" },
  ISSUED: { label: "Issued", tone: "green" },
  CANCELLED: { label: "Cancelled", tone: "red" },
  REJECTED: { label: "Declined", tone: "gray" },
} as const;
