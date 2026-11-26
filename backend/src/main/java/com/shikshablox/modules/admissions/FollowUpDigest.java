package com.shikshablox.modules.admissions;

import com.shikshablox.platform.notification.NotificationService;
import com.shikshablox.platform.tenancy.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 8:00 am IST: emails each counsellor how many follow-ups are due today. Unassigned leads go to the
 * school's administrators. One instance sends (transaction-scoped advisory lock).
 */
@Component
class FollowUpDigest {

  private static final Logger log = LoggerFactory.getLogger(FollowUpDigest.class);
  private static final long LOCK_KEY = 0x5B_41_44_4D_44_49_47L;

  private final AdmissionsService admissions;
  private final NotificationService notifications;
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final String adminBaseUrl;

  FollowUpDigest(
      AdmissionsService admissions,
      NotificationService notifications,
      JdbcClient jdbc,
      TransactionTemplate tx,
      @Value("${shikshablox.admin-base-url:http://admin.shikshablox.localhost}")
          String adminBaseUrl) {
    this.admissions = admissions;
    this.notifications = notifications;
    this.jdbc = jdbc;
    this.tx = tx;
    this.adminBaseUrl = adminBaseUrl;
  }

  @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Kolkata")
  void send() {
    TenantContext.runAsSystem(
        "admissions follow-up digest",
        () ->
            tx.executeWithoutResult(s -> {
              Boolean locked =
                  jdbc.sql("select pg_try_advisory_xact_lock(?)")
                      .param(LOCK_KEY)
                      .query(Boolean.class)
                      .single();
              if (!Boolean.TRUE.equals(locked)) {
                return;
              }
              for (AdmissionsService.DueSummary d : admissions.dueToday()) {
                try {
                  List<String> to = recipients(d.organizationId(), d.assignedTo());
                  if (to.isEmpty()) {
                    continue;
                  }
                  String school =
                      jdbc.sql("select name from organization where id = ?")
                          .param(d.organizationId())
                          .query(String.class)
                          .single();
                  notifications.email(
                      NotificationService.Template.FOLLOW_UPS_DUE,
                      to,
                      Map.of(
                          "count",
                          String.valueOf(d.due()),
                          "overdue",
                          String.valueOf(d.overdue()),
                          "school",
                          school,
                          "link",
                          adminBaseUrl + "/admissions?due=today"));
                } catch (RuntimeException e) {
                  log.warn(
                      "Follow-up digest failed for {}: {}", d.organizationId(), e.getMessage());
                }
              }
            }));
  }

  private List<String> recipients(UUID org, UUID assignee) {
    if (assignee != null) {
      return jdbc.sql("select email from app_user where id = ? and status = 'ACTIVE'")
          .param(assignee)
          .query(String.class)
          .list();
    }
    return jdbc.sql(
            "select distinct u.email from role_assignment r join app_user u on u.id = r.user_id"
                + " where r.organization_id = ? and r.role = 'SCHOOL_ADMIN' and u.status = 'ACTIVE'")
        .param(org)
        .query(String.class)
        .list();
  }
}
