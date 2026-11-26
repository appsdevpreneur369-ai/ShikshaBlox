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
