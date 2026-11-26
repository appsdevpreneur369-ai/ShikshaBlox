import { describe, expect, it } from "vitest";
import { toEnquiry, validateDemoRequest, type DemoRequest } from "./demo-request";

const valid: DemoRequest = {
  name: "Anita Rao",
  school: "Sunrise Public School",
  role: "Principal",
  phone: "+91 98765 43210",
  email: "anita@example.org",
  city: "Guntur",
  students: "600–1,500",
  plan: "standard",
  message: "We want to see fees and attendance.",
  consent: true,
};

describe("validateDemoRequest", () => {
  it("accepts a complete request", () => {
    expect(validateDemoRequest(valid)).toEqual({});
  });
  it("requires name, school, phone and consent", () => {
    const e = validateDemoRequest({});
    expect(Object.keys(e).sort()).toEqual(["consent", "name", "phone", "school"]);
  });
  it("rejects bad phone and email, but email is optional", () => {
    expect(validateDemoRequest({ ...valid, phone: "12ab" }).phone).toBeDefined();
    expect(validateDemoRequest({ ...valid, email: "not-an-email" }).email).toBeDefined();
    expect(validateDemoRequest({ ...valid, email: "" })).toEqual({});
  });
  it("limits message length", () => {
    expect(validateDemoRequest({ ...valid, message: "x".repeat(1201) }).message).toBeDefined();
  });
});

describe("toEnquiry", () => {
  it("maps to the platform enquiry contract", () => {
    const p = toEnquiry(valid, "https://shikshablox.com/contact");
    expect(p).toMatchObject({ name: "Anita Rao", phone: "+91 98765 43210", consent: true, website: "" });
    expect(p.message).toContain("School: Sunrise Public School");
    expect(p.message).toContain("Students: 600–1,500");
    expect(p.gradeSought).toBe("Plan: standard");
  });
  it("keeps the honeypot so the backend can drop bots silently", () => {
    expect(toEnquiry({ ...valid, website: "spam.example" }, "/").website).toBe("spam.example");
  });
});
