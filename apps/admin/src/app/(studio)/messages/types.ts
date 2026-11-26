export type Template = {
  id: string;
  name: string;
  language: string;
  category: string;
  subject?: string | null;
  body: string;
  whatsappStatus: string;
  smsDltId?: string | null;
};
export type TagCount = { tag: string; contacts: number };
export type Contact = {
  id: string;
  name: string;
  phone?: string | null;
  email?: string | null;
  language: string;
  tags: string[];
  studentNames?: string | null;
  whatsappOptIn: boolean;
  smsOptIn: boolean;
  emailOptIn: boolean;
  createdAt: string;
};
export type Settings = {
  quietStart: string;
  quietEnd: string;
  monthlyCap: number;
  provider: string;
  whatsappRatePaise: number;
  smsRatePaise: number;
  balancePaise: number;
  sentThisMonth: number;
};

export const CHANNEL_LABEL: Record<string, string> = {
  WHATSAPP: "WhatsApp",
  SMS: "SMS",
  EMAIL: "Email",
};
export const LANGUAGES: Record<string, string> = {
  en: "English",
  hi: "Hindi",
  te: "Telugu",
  ta: "Tamil",
  kn: "Kannada",
  ml: "Malayalam",
  mr: "Marathi",
  bn: "Bengali",
  gu: "Gujarati",
  pa: "Punjabi",
  ur: "Urdu",
  or: "Odia",
};
