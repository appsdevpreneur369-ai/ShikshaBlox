package com.shikshablox.platform.identity;

import static com.shikshablox.platform.identity.Permission.ADMISSIONS_MANAGE;
import static com.shikshablox.platform.identity.Permission.ATTENDANCE_MANAGE;
import static com.shikshablox.platform.identity.Permission.ATTENDANCE_MARK;
import static com.shikshablox.platform.identity.Permission.AUDIT_VIEW;
import static com.shikshablox.platform.identity.Permission.COMMS_MANAGE;
import static com.shikshablox.platform.identity.Permission.COMPLIANCE_MANAGE;
import static com.shikshablox.platform.identity.Permission.CONTENT_EDIT;
import static com.shikshablox.platform.identity.Permission.CONTENT_PUBLISH;
import static com.shikshablox.platform.identity.Permission.CONTENT_VIEW;
import static com.shikshablox.platform.identity.Permission.ENGAGEMENT_MANAGE;
import static com.shikshablox.platform.identity.Permission.ENQUIRY_MANAGE;
import static com.shikshablox.platform.identity.Permission.FRONT_OFFICE_MANAGE;
import static com.shikshablox.platform.identity.Permission.PLATFORM_MANAGE;
import static com.shikshablox.platform.identity.Permission.SCHOOL_MANAGE;
import static com.shikshablox.platform.identity.Permission.STUDENTS_MANAGE;

import java.util.EnumSet;
import java.util.Set;

/**
 * School and platform roles. {@link #PLATFORM_SUPER_ADMIN} is platform-wide (no organization) and
 * requires TOTP MFA; every other role is scoped to one organization.
 */
public enum Role {
  PLATFORM_SUPER_ADMIN(
      true,
      EnumSet.of(
          PLATFORM_MANAGE,
          SCHOOL_MANAGE,
          CONTENT_EDIT,
          CONTENT_PUBLISH,
          CONTENT_VIEW,
          ENQUIRY_MANAGE,
          ADMISSIONS_MANAGE,
          COMPLIANCE_MANAGE,
          FRONT_OFFICE_MANAGE,
          ENGAGEMENT_MANAGE,
          COMMS_MANAGE,
          STUDENTS_MANAGE,
          ATTENDANCE_MARK,
          ATTENDANCE_MANAGE,
          AUDIT_VIEW)),
  SCHOOL_ADMIN(
      false,
      EnumSet.of(
          SCHOOL_MANAGE,
          CONTENT_EDIT,
          CONTENT_PUBLISH,
          CONTENT_VIEW,
          ENQUIRY_MANAGE,
          ADMISSIONS_MANAGE,
          COMPLIANCE_MANAGE,
          FRONT_OFFICE_MANAGE,
          ENGAGEMENT_MANAGE,
          COMMS_MANAGE,
          STUDENTS_MANAGE,
          ATTENDANCE_MARK,
          ATTENDANCE_MANAGE,
          AUDIT_VIEW)),
  CONTENT_APPROVER(false, EnumSet.of(CONTENT_EDIT, CONTENT_PUBLISH, CONTENT_VIEW)),
  CONTENT_EDITOR(false, EnumSet.of(CONTENT_EDIT, CONTENT_VIEW)),
  VIEWER(false, EnumSet.of(CONTENT_VIEW)),
  /** Works admission leads and the enquiry inbox; read-only elsewhere. */
  ADMISSIONS_COUNSELLOR(false, EnumSet.of(ADMISSIONS_MANAGE, ENQUIRY_MANAGE, CONTENT_VIEW)),
  /** Reception: front desk registers, appointments, post, lost & found. */
  FRONT_OFFICE(false, EnumSet.of(FRONT_OFFICE_MANAGE, ENGAGEMENT_MANAGE, CONTENT_VIEW)),
  /** Teacher: attendance and leave for their assigned sections. */
  TEACHER(false, EnumSet.of(ATTENDANCE_MARK, CONTENT_VIEW));

  private final boolean platform;
  private final Set<Permission> permissions;

  Role(boolean platform, Set<Permission> permissions) {
    this.platform = platform;
    this.permissions = Set.copyOf(permissions);
  }

  public boolean platform() {
    return platform;
  }

  public Set<Permission> permissions() {
    return permissions;
  }
}
