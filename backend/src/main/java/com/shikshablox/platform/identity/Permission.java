package com.shikshablox.platform.identity;

/**
 * Fine-grained permissions (Spring Security authorities). Endpoints check permissions, never role
 * names, so roles can be re-shaped without touching controllers.
 */
public enum Permission {
  /** Platform administration: schools, plans, platform users. */
  PLATFORM_MANAGE("platform:manage"),
  /** School settings, users and roles, domains, entitlements view. */
  SCHOOL_MANAGE("school:manage"),
  /** Create and edit pages, content and media (drafts). */
  CONTENT_EDIT("content:edit"),
  /** Approve and publish content. */
  CONTENT_PUBLISH("content:publish"),
  /** Read-only access to the studio. */
  CONTENT_VIEW("content:view"),
  /** Read and manage admissions enquiries. */
  ENQUIRY_MANAGE("enquiry:manage"),
  /** Work the admissions CRM: leads, follow-ups, seats (M03). */
  ADMISSIONS_MANAGE("admissions:manage"),
  /** Compliance calendar, evidence vault and export packs (M02). */
  COMPLIANCE_MANAGE("compliance:manage"),
  /** Front office: walk-in and call register, appointments, post, lost & found (M43). */
  FRONT_OFFICE_MANAGE("frontoffice:manage"),
  /** PTM bookings, parent helpdesk and surveys (M26). */
  ENGAGEMENT_MANAGE("engagement:manage"),
  /** Communication hub: contacts, templates, broadcasts, messaging settings (M13). */
  COMMS_MANAGE("comms:manage"),
  /** Students, guardians, classes, enrolments and promotion (M04). */
  STUDENTS_MANAGE("students:manage"),
  /** Take attendance and decide leave for the sections a teacher is assigned to (M07). */
  ATTENDANCE_MARK("attendance:mark"),
  /** All sections' attendance, settings, corrections approval, registers and reports (M07). */
  ATTENDANCE_MANAGE("attendance:manage"),
  /** Enter marks, co-scholastic grades and remarks for assigned subjects and classes (M11). */
  MARKS_ENTER("marks:enter"),
  /** Subjects, exams, timetable, moderation, publishing and report cards for all classes (M11). */
  EXAMS_MANAGE("exams:manage"),
  /** Read the audit log. */
  AUDIT_VIEW("audit:view");

  private final String authority;

  Permission(String authority) {
    this.authority = authority;
  }

  public String authority() {
    return authority;
  }
}
