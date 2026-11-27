package com.shikshablox.modules.exams;

import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.identity.CurrentUser;
import com.shikshablox.platform.identity.Permission;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Exam permissions: the exam cell / office (exams:manage) works with everything; a class teacher
 * enters all subjects and remarks of their class; a subject teacher enters their subject.
 */
@Component
class ExamAccess {

  static final String MODULE = "EXAMS";
  static final ZoneId IST = ZoneId.of("Asia/Kolkata");

  private final JdbcClient jdbc;
  private final EntitlementService entitlements;

  ExamAccess(
      JdbcClient jdbc,
      @org.springframework.context.annotation.Lazy EntitlementService entitlements) {
    this.jdbc = jdbc;
    this.entitlements = entitlements;
  }

  void requireModule() {
    entitlements.requireModule(MODULE);
  }

  boolean hasModule() {
    return entitlements.hasModule(MODULE);
  }

  boolean feature(String f) {
    return entitlements.hasFeature(MODULE, f);
  }

  void requireFeature(String f, String what) {
    if (!feature(f)) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "feature_not_in_plan", what + " is part of ShikshaBlox Standard.");
    }
  }

  boolean hasModule(String module) {
    return entitlements.hasModule(module);
  }

  boolean canManage() {
    return CurrentUser.get()
        .map(u ->
            u.roles().stream().anyMatch(r -> r.permissions().contains(Permission.EXAMS_MANAGE)))
        .orElse(false);
  }

  void requireManage() {
    if (!canManage()) {
      throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Only the exam cell can do that.");
    }
  }

  UUID userId() {
    return TenantContext.userId().orElse(null);
  }

  static LocalDate today() {
    return LocalDate.now(IST);
  }

  String currentYear() {
    return jdbc.sql("select current_year from sis_settings")
        .query(String.class)
        .optional()
        .orElseGet(() -> {
          LocalDate d = today();
          int start = d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
          return start + "-" + String.format("%02d", (start + 1) % 100);
        });
  }

  boolean isClassTeacher(UUID sectionId) {
    return jdbc.sql(
                "select count(*) from attendance_teacher where class_section_id = ? and user_id = ?")
            .params(sectionId, userId())
            .query(Long.class)
            .single()
        > 0;
  }

  boolean teaches(UUID sectionId, UUID subjectId) {
    return jdbc.sql(
                "select count(*) from subject_teacher where class_section_id = ? and subject_id = ? and user_id = ?")
            .params(sectionId, subjectId, userId())
            .query(Long.class)
            .single()
        > 0;
  }

  /** May this user enter marks for the subject in the section? */
  void requireEntry(UUID sectionId, UUID subjectId) {
    if (canManage() || isClassTeacher(sectionId) || teaches(sectionId, subjectId)) {
      return;
    }
    throw new ApiException(
        HttpStatus.FORBIDDEN, "not_your_class", "You don't teach this subject in this class.");
  }

  /** May this user see report cards / remarks of the section? */
  void requireClass(UUID sectionId) {
    if (canManage() || isClassTeacher(sectionId)) {
      return;
    }
    throw new ApiException(
        HttpStatus.FORBIDDEN, "not_your_class", "Only the class teacher or exam cell can do that.");
  }
}
