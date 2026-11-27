package com.shikshablox.modules.attendance;

import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.identity.CurrentUser;
import com.shikshablox.platform.identity.Permission;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Who may see which sections: attendance managers (school admins) see every section of the current
 * year; teachers see the sections they are assigned to.
 */
@Component
class AttendanceAccess {

  static final String MODULE = "ATTENDANCE";
  static final ZoneId IST = ZoneId.of("Asia/Kolkata");

  private final JdbcClient jdbc;
  private final EntitlementService entitlements;

  AttendanceAccess(
      JdbcClient jdbc,
      @org.springframework.context.annotation.Lazy EntitlementService entitlements) {
    this.jdbc = jdbc;
    this.entitlements = entitlements;
  }

  void requireModule() {
    entitlements.requireModule(MODULE);
  }

  boolean feature(String f) {
    return entitlements.hasFeature(MODULE, f);
  }

  boolean hasModule(String module) {
    return entitlements.hasModule(module);
  }

  boolean canManage() {
    return CurrentUser.get()
        .map(u ->
            u.roles().stream()
                .anyMatch(r -> r.permissions().contains(Permission.ATTENDANCE_MANAGE)))
        .orElse(false);
  }

  void requireManage() {
    if (!canManage()) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "forbidden", "Only the school office can do that.");
    }
  }

  UUID userId() {
    return TenantContext.userId().orElse(null);
  }

  static LocalDate today() {
    return LocalDate.now(IST);
  }

  /** The school's current academic year (from student information, or the calendar). */
  String currentYear() {
    return jdbc.sql("select current_year from sis_settings")
        .query(String.class)
        .optional()
        .orElseGet(() -> defaultYear(today()));
  }

  static String defaultYear(LocalDate d) {
    int start = d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
    return start + "-" + String.format("%02d", (start + 1) % 100);
  }

  static LocalDate yearStart(String year) {
    return LocalDate.of(Integer.parseInt(year.substring(0, 4)), 4, 1);
  }

  static LocalDate yearEnd(String year) {
    return yearStart(year).plusYears(1).minusDays(1);
  }

  /** Sections of the current year this user may work with. */
  List<UUID> sectionIds() {
    String year = currentYear();
    if (canManage()) {
      return jdbc.sql("select id from class_section where academic_year = ?")
          .param(year)
          .query(UUID.class)
          .list();
    }
    return jdbc.sql(
            "select c.id from attendance_teacher t join class_section c on c.id = t.class_section_id"
                + " where t.user_id = ? and c.academic_year = ?")
        .params(userId(), year)
        .query(UUID.class)
        .list();
  }

  /** Fails unless the user may work with the section (any year, for registers of past years). */
  void requireSection(UUID sectionId) {
    boolean exists =
        jdbc.sql("select count(*) from class_section where id = ?")
                .param(sectionId)
                .query(Long.class)
                .single()
            > 0;
    if (!exists) {
      throw ApiException.notFound("Class");
    }
    if (canManage()) {
      return;
    }
    boolean assigned =
        jdbc.sql(
                    "select count(*) from attendance_teacher where class_section_id = ? and user_id = ?")
                .params(sectionId, userId())
                .query(Long.class)
                .single()
            > 0;
    if (!assigned) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "not_your_class", "You are not assigned to this class.");
    }
  }

  /** Fails unless the student is in a section of the current year this user may work with. */
  void requireStudent(UUID studentId) {
    if (canManage()) {
      boolean exists =
          jdbc.sql("select count(*) from student where id = ?")
                  .param(studentId)
                  .query(Long.class)
                  .single()
              > 0;
      if (!exists) {
        throw ApiException.notFound("Student");
      }
      return;
    }
    boolean ok =
        jdbc.sql(
                    "select count(*) from student_enrolment e join attendance_teacher t on t.class_section_id = e.class_section_id"
                        + " where e.student_id = ? and e.academic_year = ? and t.user_id = ?")
                .params(studentId, currentYear(), userId())
                .query(Long.class)
                .single()
            > 0;
    if (!ok) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "not_your_class", "This student is not in your class.");
    }
  }
}
