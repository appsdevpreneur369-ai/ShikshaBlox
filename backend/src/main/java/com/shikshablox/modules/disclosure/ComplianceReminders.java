package com.shikshablox.modules.disclosure;

import com.shikshablox.platform.notification.NotificationService;
import com.shikshablox.platform.tenancy.TenantContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 8:15 am IST: one email per person with the compliance items and certificates that need attention.
 * Items go to their owner (or the school admins); certificates go to the school admins. One
 * instance sends (transaction-scoped advisory lock).
 */
@Component
class ComplianceReminders {

  private static final Logger log = LoggerFactory.getLogger(ComplianceReminders.class);
  private static final long LOCK_KEY = 0x5B_43_4F_4D_50_4C_59L;

  private final ComplianceService compliance;
  private final NotificationService notifications;
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final String adminBaseUrl;

  ComplianceReminders(
      ComplianceService compliance,
      NotificationService notifications,
      JdbcClient jdbc,
      TransactionTemplate tx,
      @Value("${shikshablox.admin-base-url:http://admin.shikshablox.localhost}")
          String adminBaseUrl) {
    this.compliance = compliance;
    this.notifications = notifications;
    this.jdbc = jdbc;
    this.tx = tx;
    this.adminBaseUrl = adminBaseUrl;
  }

  @Scheduled(cron = "0 15 8 * * *", zone = "Asia/Kolkata")
  void send() {
    TenantContext.runAsSystem(
        "compliance reminders",
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
              // organization -> recipient email -> lines
              Map<UUID, Map<String, List<String>>> byOrg = new LinkedHashMap<>();
              for (ComplianceService.Reminder r :
                  compliance.dueReminders(ComplianceService.today())) {
                for (String to : recipients(r.organizationId(), r.ownerId())) {
                  byOrg
                      .computeIfAbsent(r.organizationId(), k -> new LinkedHashMap<>())
                      .computeIfAbsent(to, k -> new ArrayList<>())
                      .add(r.line());
                }
              }
              byOrg.forEach((org, people) -> {
                try {
                  String school =
                      jdbc.sql("select name from organization where id = ?")
                          .param(org)
                          .query(String.class)
                          .single();
                  people.forEach((to, lines) ->
                      notifications.email(
                          NotificationService.Template.COMPLIANCE_DUE,
                          List.of(to),
                          Map.of(
                              "school", school,
                              "items", "• " + String.join("\n• ", lines),
                              "link", adminBaseUrl + "/compliance")));
                } catch (RuntimeException e) {
                  log.warn("Compliance reminders failed for {}: {}", org, e.getMessage());
                }
              });
            }));
  }

  private List<String> recipients(UUID org, UUID owner) {
    if (owner != null) {
      List<String> o =
          jdbc.sql("select email from app_user where id = ? and status = 'ACTIVE'")
              .param(owner)
              .query(String.class)
              .list();
      if (!o.isEmpty()) {
        return o;
      }
    }
    return jdbc
        .sql(
            "select distinct u.email from role_assignment r join app_user u on u.id = r.user_id"
                + " where r.organization_id = ? and r.role = 'SCHOOL_ADMIN' and u.status = 'ACTIVE'")
        .param(org)
        .query(String.class)
        .list()
        .stream()
        .filter(Objects::nonNull)
        .toList();
  }
}
