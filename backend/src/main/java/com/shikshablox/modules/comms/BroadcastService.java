package com.shikshablox.modules.comms;

import static com.shikshablox.modules.comms.CommsService.optional;
import static com.shikshablox.modules.comms.CommsService.required;

import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.notification.NotificationService;
import com.shikshablox.platform.shared.RateLimiter;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.TenantContext;
import com.shikshablox.platform.tenancy.TenantResolver;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Broadcasts (circulars and alerts). On send, each contact in the audience gets one delivery on the
 * first channel in the broadcast's order that they have opted in to and that the template is ready
 * for (WhatsApp: approved; SMS: DLT id; email: always). Messages are short and carry a personal
 * link to the circular page, where opening counts as read and the parent can acknowledge (and
 * answer a consent question) or stop messages. Quiet hours defer non-emergency sends; a monthly cap
 * and the prepaid wallet stop runaway costs.
 */
@Service
public class BroadcastService {

  private static final Logger log = LoggerFactory.getLogger(BroadcastService.class);
  static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Pattern VAR = Pattern.compile("\\{\\{(\\w+)}}");
  private static final long LOCK_KEY = 0x5B_43_4F_4D_4D_53L;

  public record BroadcastInput(
      String title,
      String details,
      String templateName,
      List<String> channels,
      List<String> audienceTags,
      Boolean ackRequired,
      String consentQuestion,
      Boolean emergency,
      Instant sendAt) {}

  public record Broadcast(
      UUID id,
      String title,
      String details,
      String templateName,
      List<String> channels,
      List<String> audienceTags,
      boolean ackRequired,
      String consentQuestion,
      boolean emergency,
      String status,
      Instant sendAt,
      Instant sentAt,
      Instant createdAt,
      Stats stats) {}

  public record Stats(
      long total,
      long sent,
      long failed,
      long skipped,
      long read,
      long acknowledged,
      long consentYes,
      long consentNo,
      long testMode) {}

  public record Delivery(
      UUID id,
      String contactName,
      String channel,
      String address,
      String status,
      String skipReason,
      boolean testMode,
      Instant sentAt,
      Instant readAt,
      Instant ackAt,
      Boolean consentAnswer,
      String link) {}

  public record Preview(
      long audience,
      Map<String, Long> byChannel,
      long skipped,
      Map<String, Long> skipReasons,
      long costPaise,
      boolean testMode,
      Instant willSendAt,
      String sample) {}

  /** What the parent sees on the circular page. */
  public record Circular(
      String school,
      String title,
      String details,
      Instant sentAt,
      boolean ackRequired,
      String consentQuestion,
      Instant acknowledgedAt,
      Boolean consentAnswer,
      boolean stopped) {}

  private final TenantResolver resolver;
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final RateLimiter limiter;
  private final AuditService audit;
  private final EntitlementService entitlements;
  private final CommsService comms;
  private final MessageProvider provider;
  private final NotificationService notifications;
  private final String baseDomain;
  private final Map<String, String> siteUrls = new HashMap<>();

  BroadcastService(
      TenantResolver resolver,
      JdbcClient jdbc,
      TransactionTemplate tx,
      RateLimiter limiter,
      AuditService audit,
      EntitlementService entitlements,
      CommsService comms,
      MessageProvider provider,
      NotificationService notifications,
      @Value("${shikshablox.base-domain}") String baseDomain,
      @Value("${shikshablox.site.public-urls:}") String publicUrls) {
    this.resolver = resolver;
    this.jdbc = jdbc;
    this.tx = tx;
    this.limiter = limiter;
    this.audit = audit;
    this.entitlements = entitlements;
    this.comms = comms;
    this.provider = provider;
    this.notifications = notifications;
    this.baseDomain = baseDomain;
    for (String pair : publicUrls.split("[,@]")) {
      String[] kv = pair.trim().split("=", 2);
      if (kv.length == 2 && !kv[1].isBlank()) {
        siteUrls.put(kv[0].trim(), kv[1].trim().replaceAll("/+$", ""));
      }
    }
  }

  // ------------------------------------------------------------------ studio

  public List<Broadcast> list() {
    entitlements.requireFeature(CommsService.MODULE, "broadcasts");
    List<UUID> ids =
        jdbc.sql(
                "select id from comms_broadcast where kind = 'CIRCULAR' order by created_at desc limit 200")
            .query(UUID.class)
            .list();
    return ids.stream().map(this::load).toList();
  }

  public Broadcast get(UUID id) {
    entitlements.requireFeature(CommsService.MODULE, "broadcasts");
    return load(id);
  }

  public List<Delivery> deliveries(UUID id, String filter) {
    entitlements.requireFeature(CommsService.MODULE, "broadcasts");
    String base = siteUrl();
    String where =
        switch (filter == null ? "" : filter) {
          case "unread" -> " and status in ('SENT', 'DELIVERED') and read_at is null";
          case "unacknowledged" -> " and status in ('SENT', 'DELIVERED') and ack_at is null";
          case "failed" -> " and status in ('FAILED', 'SKIPPED')";
          default -> "";
        };
    return jdbc.sql(
            "select * from comms_delivery where broadcast_id = ?"
                + where
                + " order by contact_name limit 5000")
        .param(id)
        .query((rs, i) ->
            new Delivery(
                rs.getObject("id", UUID.class),
                rs.getString("contact_name"),
                rs.getString("channel"),
                mask(rs.getString("address")),
                rs.getString("status"),
                rs.getString("skip_reason"),
                rs.getBoolean("test_mode"),
                ts(rs.getTimestamp("sent_at")),
                ts(rs.getTimestamp("read_at")),
                ts(rs.getTimestamp("ack_at")),
                (Boolean) rs.getObject("consent_answer"),
                // Staff can forward a parent's personal link by hand (e.g. before WhatsApp is
                // connected).
                base + "/c/" + rs.getString("token")))
        .list();
  }

  public Broadcast save(UUID id, BroadcastInput in) {
    entitlements.requireFeature(CommsService.MODULE, "broadcasts");
    Valid v = validate(in);
    return tx.execute(s -> {
      UUID bid;
      if (id == null) {
        bid =
            jdbc.sql(
                    "insert into comms_broadcast (organization_id, title, details, template_name, channels, audience_tags,"
                        + " ack_required, consent_question, emergency, send_at, created_by)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id")
                .params(
                    TenantContext.requireOrganizationId(),
                    v.title,
                    v.details,
                    v.template,
                    v.channels.toArray(String[]::new),
                    v.tags.toArray(String[]::new),
                    v.ack,
                    v.question,
                    v.emergency,
                    ts(in.sendAt()),
                    TenantContext.userId().orElse(null))
                .query(UUID.class)
                .single();
      } else {
        Broadcast before = load(id);
        if (!"DRAFT".equals(before.status())) {
          throw ApiException.conflict("not_draft", "Only drafts can be edited.");
        }
        jdbc.sql(
                "update comms_broadcast set title = ?, details = ?, template_name = ?, channels = ?, audience_tags = ?,"
                    + " ack_required = ?, consent_question = ?, emergency = ?, send_at = ?, updated_at = now() where id = ?")
            .params(
                v.title,
                v.details,
                v.template,
                v.channels.toArray(String[]::new),
                v.tags.toArray(String[]::new),
                v.ack,
                v.question,
                v.emergency,
                ts(in.sendAt()),
                id)
            .update();
        bid = id;
      }
      audit.record(
          "comms.broadcast_saved",
          "comms_broadcast",
          bid.toString(),
          null,
          Map.of("title", v.title));
      return load(bid);
    });
  }

  public void delete(UUID id) {
    entitlements.requireFeature(CommsService.MODULE, "broadcasts");
    tx.executeWithoutResult(s -> {
      Broadcast b = load(id);
      if ("SCHEDULED".equals(b.status()) || "SENDING".equals(b.status())) {
        throw ApiException.conflict("in_progress", "Cancel the broadcast before deleting it.");
      }
      jdbc.sql("delete from comms_broadcast where id = ?").param(id).update();
      audit.record(
          "comms.broadcast_deleted",
          "comms_broadcast",
          id.toString(),
          Map.of("title", b.title()),
          null);
    });
  }

  /** Who would receive it, on which channel, at what cost, and when (quiet hours). */
  public Preview preview(UUID id) {
    entitlements.requireFeature(CommsService.MODULE, "broadcasts");
    Broadcast b = load(id);
    CommsService.Settings st = comms.loadSettings();
    List<Plan> plan = plan(b);
    Map<String, Long> byChannel = new LinkedHashMap<>();
    Map<String, Long> reasons = new LinkedHashMap<>();
    long cost = 0;
    for (Plan p : plan) {
      if (p.channel == null) {
        reasons.merge(p.skipReason, 1L, Long::sum);
      } else {
        byChannel.merge(p.channel, 1L, Long::sum);
        cost += rate(st, p.channel);
      }
    }
    String sample =
        plan.stream().filter(p -> p.channel != null).findFirst().map(p -> p.text).orElse(null);
    Instant when = whenToSend(b, st, Instant.now());
    return new Preview(
        plan.size(),
        byChannel,
        reasons.values().stream().mapToLong(Long::longValue).sum(),
        reasons,
        cost,
        "TEST".equals(st.provider()),
        when,
        sample);
  }

  /** Queues the broadcast: now, at its scheduled time, or when quiet hours end. */
  public Broadcast send(UUID id) {
    entitlements.requireFeature(CommsService.MODULE, "broadcasts");
    return tx.execute(s -> {
      Broadcast b = load(id);
      if (!"DRAFT".equals(b.status())) {
        throw ApiException.conflict(
            "not_draft", "This broadcast has already been sent or scheduled.");
      }
      CommsService.Settings st = comms.loadSettings();
      Preview p = preview(id);
      long reach = p.byChannel().values().stream().mapToLong(Long::longValue).sum();
      if (reach == 0) {
        throw ApiException.badRequest(
            "no_recipients",
            "Nobody in this audience can receive it. Check opt-ins and the template's channel readiness.");
      }
      if (st.sentThisMonth() + reach > st.monthlyCap()) {
        throw ApiException.conflict(
            "cap_reached",
            "This would exceed your monthly limit of "
                + st.monthlyCap()
                + " messages ("
                + st.sentThisMonth()
                + " sent this month). Raise the limit in Settings.");
      }
      if (!"TEST".equals(st.provider()) && p.costPaise() > st.balancePaise()) {
        throw ApiException.conflict(
            "low_balance", "Your messaging wallet is too low for this broadcast. Please top up.");
      }
      Instant when = whenToSend(b, st, Instant.now());
      jdbc.sql(
              "update comms_broadcast set status = 'SCHEDULED', send_at = ?, updated_at = now() where id = ?")
          .params(Timestamp.from(when), id)
          .update();
      audit.record(
          "comms.broadcast_queued",
          "comms_broadcast",
          id.toString(),
          null,
          Map.of("recipients", reach, "sendAt", when.toString()));
      return load(id);
    });
  }

  public Broadcast cancel(UUID id) {
    entitlements.requireFeature(CommsService.MODULE, "broadcasts");
    return tx.execute(s -> {
      Broadcast b = load(id);
      if (!"SCHEDULED".equals(b.status())) {
        throw ApiException.conflict("not_scheduled", "Only scheduled broadcasts can be cancelled.");
      }
      jdbc.sql("update comms_broadcast set status = 'CANCELLED', updated_at = now() where id = ?")
          .param(id)
          .update();
      audit.record("comms.broadcast_cancelled", "comms_broadcast", id.toString(), null, null);
      return load(id);
    });
  }

  // ------------------------------------------------------------------ alerts (other modules)

  /** Outcome of {@link #alert}: the queued broadcast, or why nothing was sent. */
  public record AlertResult(UUID broadcastId, String skippedReason) {}

  /**
   * A one-off alert to the contacts with these phone numbers (e.g. an absence alert to a child's
   * guardians), sent like any broadcast: consent and channel readiness per contact, quiet hours,
   * the monthly cap and the wallet. The template is created as a draft with {@code defaultBody} if
   * the school has none yet (email works straight away; WhatsApp after approval). Alerts are kept
   * out of the circulars list. Runs in the caller's transaction, if any.
   */
  public AlertResult alert(
      String title, String details, String templateName, String defaultBody, List<String> phones) {
    if (comms.variants(templateName).isEmpty()) {
      jdbc.sql(
              "insert into comms_template (organization_id, name, language, category, subject, body)"
                  + " values (?, ?, 'en', 'UTILITY', ?, ?) on conflict do nothing")
          .params(
              TenantContext.requireOrganizationId(),
              templateName,
              "{{school}}: {{title}}",
              defaultBody)
          .update();
    }
    List<String> keys =
        phones.stream().filter(p -> p != null).map(CommsService::key).distinct().toList();
    if (keys.isEmpty()) {
      return new AlertResult(null, "No guardian phone number");
    }
    List<UUID> contacts =
        jdbc.sql(
                "select id from comms_contact where right(regexp_replace(phone, '\\D', '', 'g'), 10) = any(?::text[])")
            .param(keys.toArray(String[]::new))
            .query(UUID.class)
            .list();
    if (contacts.isEmpty()) {
      return new AlertResult(null, "Guardian is not in the message contacts");
    }
    CommsService.Settings st = comms.loadSettings();
    if (st.sentThisMonth() + contacts.size() > st.monthlyCap()) {
      return new AlertResult(null, "Monthly message limit reached");
    }
    UUID id =
        jdbc.sql(
                "insert into comms_broadcast (organization_id, title, details, template_name, channels, kind,"
                    + " audience_contacts, status, send_at, created_by)"
                    + " values (?, ?, ?, ?, ?, 'ALERT', ?, 'DRAFT', null, ?) returning id")
            .params(
                TenantContext.requireOrganizationId(),
                truncate(title, 160),
                truncate(details, 8000),
                templateName,
                new String[] {"WHATSAPP", "SMS", "EMAIL"},
                contacts.toArray(UUID[]::new),
                TenantContext.userId().orElse(null))
            .query(UUID.class)
            .single();
    Broadcast b = load(id);
    long reach = plan(b).stream().filter(p -> p.channel != null).count();
    if (reach == 0) {
      jdbc.sql("delete from comms_broadcast where id = ?").param(id).update();
      return new AlertResult(null, "No consent or no ready template for any channel");
    }
    jdbc.sql(
            "update comms_broadcast set status = 'SCHEDULED', send_at = ?, updated_at = now() where id = ?")
        .params(Timestamp.from(whenToSend(b, st, Instant.now())), id)
        .update();
    return new AlertResult(id, null);
  }

  /** Delivery counts of alerts (or any broadcasts), by broadcast id. */
  public Map<UUID, Stats> stats(java.util.Collection<UUID> ids) {
    Map<UUID, Stats> out = new HashMap<>();
    for (UUID id : new HashSet<>(ids)) {
      if (id != null) {
        jdbc.sql("select 1 from comms_broadcast where id = ?")
            .param(id)
            .query(Integer.class)
            .optional()
            .ifPresent(x -> out.put(id, load(id).stats()));
      }
    }
    return out;
  }

  private static String truncate(String s, int max) {
    return s == null || s.length() <= max ? s : s.substring(0, max);
  }

  // ------------------------------------------------------------------ dispatcher

  /** Every 30 s: start due broadcasts and send their queued deliveries (one instance at a time). */
  @Scheduled(fixedDelay = 30_000, initialDelay = 20_000)
  void dispatch() {
    List<UUID[]> due =
        TenantContext.callAsSystem(
            "comms dispatcher",
            () ->
                tx.execute(s -> {
                  Boolean locked =
                      jdbc.sql("select pg_try_advisory_xact_lock(?)")
                          .param(LOCK_KEY)
                          .query(Boolean.class)
                          .single();
                  if (!Boolean.TRUE.equals(locked)) {
                    return List.<UUID[]>of();
                  }
                  return jdbc.sql(
                          "select id, organization_id from comms_broadcast where (status = 'SCHEDULED' and send_at <= now())"
                              + " or status = 'SENDING' order by send_at limit 20")
                      .query((rs, i) ->
                          new UUID[] {rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)})
                      .list();
                }));
    for (UUID[] d : due) {
      try (var ignored = TenantContext.forTenant(d[1], null)) {
        run(d[0]);
      } catch (RuntimeException e) {
        log.warn("Broadcast {} dispatch failed: {}", d[0], e.getMessage());
      }
    }
  }

  /**
   * Processes a queued broadcast straight away instead of waiting for the dispatcher (used by the
   * demo seeder).
   */
  public void runNow(UUID id) {
    for (int i = 0; i < 50 && !"SENT".equals(load(id).status()); i++) {
      run(id);
    }
  }

  /** Creates deliveries (once), then sends up to 300 queued ones; marks SENT when none remain. */
  void run(UUID id) {
    Broadcast b = load(id);
    if ("SCHEDULED".equals(b.status())) {
      boolean claimed =
          tx.execute(s -> {
            int n =
                jdbc.sql(
                        "update comms_broadcast set status = 'SENDING', updated_at = now() where id = ? and status = 'SCHEDULED'")
                    .param(id)
                    .update();
            if (n == 0) {
              return false;
            }
            for (Plan p : plan(b)) {
              jdbc.sql(
                      "insert into comms_delivery (organization_id, broadcast_id, contact_id, contact_name, channel, address,"
                          + " token, status, skip_reason) values (?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict do nothing")
                  .params(
                      TenantContext.requireOrganizationId(),
                      id,
                      p.contactId,
                      p.contactName,
                      p.channel,
                      p.address,
                      p.token,
                      p.channel == null ? "SKIPPED" : "QUEUED",
                      p.skipReason)
                  .update();
            }
            return true;
          });
      if (!claimed) {
        return;
      }
    }
    CommsService.Settings st = comms.loadSettings();
    List<Map<String, Object>> queued =
        jdbc.sql(
                "select d.id, d.contact_id, d.contact_name, d.channel, d.address, d.token, coalesce(c.language, 'en') lang"
                    + " from comms_delivery d left join comms_contact c on c.id = d.contact_id"
                    + " where d.broadcast_id = ? and d.status = 'QUEUED' limit 300")
            .param(id)
            .query((rs, i) -> {
              Map<String, Object> m = new HashMap<>();
              m.put("id", rs.getObject(1, UUID.class));
              m.put("name", rs.getString(3));
              m.put("channel", rs.getString(4));
              m.put("address", rs.getString(5));
              m.put("token", rs.getString(6));
              m.put("lang", rs.getString(7));
              return m;
            })
            .list();
    List<CommsService.Template> variants = comms.variants(b.templateName());
    String school = schoolName();
    String base = siteUrl();
    for (Map<String, Object> q : queued) {
      String channel = (String) q.get("channel");
      CommsService.Template t = CommsService.pick(variants, (String) q.get("lang"));
      String link = base + "/c/" + q.get("token");
      String text =
          render(
              t == null ? "{{title}}: {{link}}" : t.body(),
              (String) q.get("name"),
              school,
              b.title(),
              link);
      boolean ok;
      String ref = null;
      String error = null;
      boolean test = false;
      if ("EMAIL".equals(channel)) {
        String subject =
            t != null && t.subject() != null
                ? render(t.subject(), (String) q.get("name"), school, b.title(), link)
                : school + ": " + b.title();
        ok = notifications.plain((String) q.get("address"), subject, text);
        if (!ok) {
          error = "Email could not be sent";
        }
      } else {
        MessageProvider.Result r =
            provider.send(
                channel,
                (String) q.get("address"),
                "SMS".equals(channel) && t != null ? t.smsDltId() : b.templateName(),
                text);
        ok = r.ok();
        ref = r.reference();
        error = r.error();
        test = r.testMode();
      }
      int cost = ok && !test ? rate(st, channel) : 0;
      String finalRef = ref;
      String finalError = error;
      boolean finalTest = test;
      tx.executeWithoutResult(s -> {
        jdbc.sql(
                "update comms_delivery set status = ?, provider_ref = ?, skip_reason = ?, test_mode = ?, cost_paise = ?,"
                    + " sent_at = case when ? then now() else null end where id = ?")
            .params(ok ? "SENT" : "FAILED", finalRef, finalError, finalTest, cost, ok, q.get("id"))
            .update();
        if (cost > 0) {
          jdbc.sql("update comms_settings set balance_paise = balance_paise - ?")
              .param(cost)
              .update();
        }
      });
    }
    long remaining =
        jdbc.sql("select count(*) from comms_delivery where broadcast_id = ? and status = 'QUEUED'")
            .param(id)
            .query(Long.class)
            .single();
    if (remaining == 0) {
      tx.executeWithoutResult(s -> {
        long spent =
            jdbc.sql(
                    "select coalesce(sum(cost_paise), 0) from comms_delivery where broadcast_id = ?")
                .param(id)
                .query(Long.class)
                .single();
        jdbc.sql(
                "update comms_broadcast set status = 'SENT', sent_at = now(), updated_at = now() where id = ? and status = 'SENDING'")
            .param(id)
            .update();
        if (spent > 0) {
          jdbc.sql(
                  "insert into comms_wallet_entry (organization_id, amount_paise, reason, broadcast_id) values (?, ?, ?, ?)")
              .params(TenantContext.requireOrganizationId(), -spent, "Broadcast: " + b.title(), id)
              .update();
        }
        audit.record(
            "comms.broadcast_sent",
            "comms_broadcast",
            id.toString(),
            null,
            Map.of("costPaise", spent));
      });
    }
  }

  // ------------------------------------------------------------------ public circular page

  public Circular circular(String host, String token) {
    Organization org = resolve(host, token);
    return TenantContext.callInTenant(
        org.id(),
        () ->
            tx.execute(s -> {
              jdbc.sql(
                      "update comms_delivery set read_at = coalesce(read_at, now()) where token = ?")
                  .param(token)
                  .update();
              return circularView(token, org.name());
            }));
  }

  public Circular acknowledge(String host, String token, Boolean consent) {
    Organization org = resolve(host, token);
    return TenantContext.callInTenant(
        org.id(),
        () ->
            tx.execute(s -> {
              jdbc.sql(
                      "update comms_delivery set read_at = coalesce(read_at, now()), ack_at = coalesce(ack_at, now()),"
                          + " consent_answer = coalesce(?, consent_answer) where token = ?")
                  .params(consent, token)
                  .update();
              return circularView(token, org.name());
            }));
  }

  public Circular stop(String host, String token) {
    Organization org = resolve(host, token);
    return TenantContext.callInTenant(
        org.id(),
        () ->
            tx.execute(s -> {
              UUID contact =
                  jdbc.sql("select contact_id from comms_delivery where token = ?")
                      .param(token)
                      .query(UUID.class)
                      .optional()
                      .orElse(null);
              if (contact != null) {
                comms.withdrawAll(contact, "LINK");
                audit.record(
                    "comms.contact_stopped", "comms_contact", contact.toString(), null, null);
              }
              return circularView(token, org.name());
            }));
  }

  private Organization resolve(String host, String token) {
    if (token == null || !token.matches("^[A-Za-z0-9_-]{16,64}$")) {
      throw ApiException.notFound("Circular");
    }
    Organization org = resolver.resolve(host).orElseThrow(() -> ApiException.notFound("School"));
    if (!limiter.tryAcquire("circular:" + org.id() + ":" + token, 60, Duration.ofMinutes(10))) {
      throw ApiException.tooManyRequests();
    }
    return org;
  }

  private Circular circularView(String token, String school) {
    return jdbc.sql(
            "select b.title, b.details, coalesce(b.sent_at, d.sent_at), b.ack_required, b.consent_question, d.ack_at,"
                + " d.consent_answer, c.id is null or not (c.whatsapp_opt_in or c.sms_opt_in or c.email_opt_in)"
                + " from comms_delivery d join comms_broadcast b on b.id = d.broadcast_id"
                + " left join comms_contact c on c.id = d.contact_id where d.token = ?")
        .param(token)
        .query((rs, i) ->
            new Circular(
                school,
                rs.getString(1),
                rs.getString(2),
                ts(rs.getTimestamp(3)),
                rs.getBoolean(4),
                rs.getString(5),
                ts(rs.getTimestamp(6)),
                (Boolean) rs.getObject(7),
                rs.getBoolean(8)))
        .optional()
        .orElseThrow(() -> ApiException.notFound("Circular"));
  }

  // ------------------------------------------------------------------ planning

  private record Plan(
      UUID contactId,
      String contactName,
      String channel,
      String address,
      String skipReason,
      String token,
      String text) {}

  /** One entry per contact in the audience: chosen channel (or why it is skipped). */
  List<Plan> plan(Broadcast b) {
    List<CommsService.Template> variants = comms.variants(b.templateName());
    StringBuilder sql =
        new StringBuilder(
            "select id, name, phone, email, language, whatsapp_opt_in, sms_opt_in, email_opt_in from comms_contact");
    List<Object> p = new ArrayList<>();
    UUID[] direct =
        jdbc.sql("select audience_contacts from comms_broadcast where id = ?")
            .param(b.id())
            .query((rs, i) -> (UUID[]) rs.getArray(1).getArray())
            .optional()
            .orElse(new UUID[0]);
    if (direct.length > 0) {
      sql.append(" where id = any(?::uuid[])");
      p.add(direct);
    } else if (!b.audienceTags().isEmpty()) {
      sql.append(" where tags && ?::text[]");
      p.add(b.audienceTags().toArray(String[]::new));
    }
    sql.append(" order by name");
    String school = schoolName();
    String base = siteUrl();
    Set<String> seen = new HashSet<>();
    List<Plan> out = new ArrayList<>();
    jdbc.sql(sql.toString())
        .params(p)
        .query((rs, i) -> {
          UUID id = rs.getObject("id", UUID.class);
          String name = rs.getString("name");
          String phone = rs.getString("phone");
          String email = rs.getString("email");
          CommsService.Template t = CommsService.pick(variants, rs.getString("language"));
          String channel = null;
          String address = null;
          String reason = "No consent for the chosen channels";
          for (String ch : b.channels()) {
            boolean optedIn =
                switch (ch) {
                  case "WHATSAPP" -> rs.getBoolean("whatsapp_opt_in") && phone != null;
                  case "SMS" -> rs.getBoolean("sms_opt_in") && phone != null;
                  default -> rs.getBoolean("email_opt_in") && email != null;
                };
            if (!optedIn) {
              continue;
            }
            boolean ready =
                t != null
                    && switch (ch) {
                      case "WHATSAPP" -> "APPROVED".equals(t.whatsappStatus());
                      case "SMS" -> t.smsDltId() != null;
                      default -> true;
                    };
            if (!ready) {
              reason = "Template not ready for " + ch.toLowerCase(java.util.Locale.ROOT);
              continue;
            }
            String addr =
                "EMAIL".equals(ch)
                    ? email.toLowerCase(java.util.Locale.ROOT)
                    : CommsService.key(phone);
            // Duplicate suppression: one message per address per broadcast.
            if (!seen.add(ch + ":" + addr)) {
              reason = "Duplicate of another contact";
              continue;
            }
            channel = ch;
            address = "EMAIL".equals(ch) ? email : phone;
            break;
          }
          String token = token();
          String text =
              channel == null
                  ? null
                  : render(t.body(), name, school, b.title(), base + "/c/" + token);
          out.add(
              new Plan(id, name, channel, address, channel == null ? reason : null, token, text));
          return null;
        })
        .list();
    return out;
  }

  /** Non-emergency broadcasts wait for the end of quiet hours (IST). */
  static Instant whenToSend(Broadcast b, CommsService.Settings st, Instant now) {
    Instant at = b.sendAt() != null && b.sendAt().isAfter(now) ? b.sendAt() : now;
    if (b.emergency()) {
      return at;
    }
    ZonedDateTime z = at.atZone(IST);
    LocalTime t = z.toLocalTime();
    LocalTime qs = st.quietStart();
    LocalTime qe = st.quietEnd();
    boolean quiet =
        qs.isAfter(qe) ? (!t.isBefore(qs) || t.isBefore(qe)) : (!t.isBefore(qs) && t.isBefore(qe));
    if (!quiet || qs.equals(qe)) {
      return at;
    }
    ZonedDateTime end = z.with(qe).withSecond(0).withNano(0);
    if (!end.isAfter(z)) {
      end = end.plusDays(1);
    }
    return end.toInstant();
  }

  static String render(String body, String name, String school, String title, String link) {
    Map<String, String> vars =
        Map.of("name", name == null ? "" : name, "school", school, "title", title, "link", link);
    Matcher m = VAR.matcher(body);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(sb, Matcher.quoteReplacement(vars.getOrDefault(m.group(1), "")));
    }
    m.appendTail(sb);
    String out = sb.toString();
    // Every message must carry the circular link (details stay behind it).
    return out.contains(link) ? out : out + " " + link;
  }

  private static int rate(CommsService.Settings st, String channel) {
    return switch (channel) {
      case "WHATSAPP" -> st.whatsappRatePaise();
      case "SMS" -> st.smsRatePaise();
      default -> 0;
    };
  }

  private static String token() {
    byte[] b = new byte[18];
    RANDOM.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  private String schoolName() {
    UUID org = TenantContext.requireOrganizationId();
    return jdbc.sql("select name from organization where id = ?")
        .param(org)
        .query(String.class)
        .single();
  }

  /** The school's public website address (custom domain, staging override, or subdomain). */
  public String siteUrl() {
    UUID org = TenantContext.requireOrganizationId();
    String slug =
        jdbc.sql("select slug from organization where id = ?")
            .param(org)
            .query(String.class)
            .single();
    if (siteUrls.containsKey(slug)) {
      return siteUrls.get(slug);
    }
    String domain =
        jdbc.sql(
                "select host from site_domain where status = 'VERIFIED' order by is_primary desc, created_at limit 1")
            .query(String.class)
            .optional()
            .orElse(null);
    return "https://" + (domain != null ? domain : slug + "." + baseDomain);
  }

  private record Valid(
      String title,
      String details,
      String template,
      List<String> channels,
      List<String> tags,
      boolean ack,
      String question,
      boolean emergency) {}

  private Valid validate(BroadcastInput in) {
    String title = required(in.title(), 160, "Title");
    String details = optional(in.details(), 8000, "Details");
    String template = required(in.templateName(), 60, "Template");
    if (comms.variants(template).isEmpty()) {
      throw ApiException.badRequest("invalid_template", "Choose a template.");
    }
    List<String> channels = new ArrayList<>();
    for (String c : in.channels() == null ? List.<String>of() : in.channels()) {
      String ch = CommsService.oneOf(c, CommsService.CHANNELS, "channel");
      if (!channels.contains(ch)) {
        channels.add(ch);
      }
    }
    if (channels.isEmpty()) {
      throw ApiException.badRequest("missing_channel", "Choose at least one channel.");
    }
    List<String> tags =
        in.audienceTags() == null ? List.of() : CommsService.cleanTags(in.audienceTags());
    String question = optional(in.consentQuestion(), 300, "Consent question");
    boolean ack = Boolean.TRUE.equals(in.ackRequired()) || question != null;
    if (in.sendAt() != null && in.sendAt().isAfter(Instant.now().plus(Duration.ofDays(60)))) {
      throw ApiException.badRequest("invalid_time", "Schedule up to 60 days ahead.");
    }
    return new Valid(
        title,
        details,
        template,
        channels,
        tags,
        ack,
        question,
        Boolean.TRUE.equals(in.emergency()));
  }

  private Broadcast load(UUID id) {
    Stats stats =
        jdbc.sql(
                "select count(*), count(*) filter (where status in ('SENT', 'DELIVERED')), count(*) filter (where status = 'FAILED'),"
                    + " count(*) filter (where status = 'SKIPPED'), count(read_at), count(ack_at),"
                    + " count(*) filter (where consent_answer), count(*) filter (where consent_answer = false),"
                    + " count(*) filter (where test_mode) from comms_delivery where broadcast_id = ?")
            .param(id)
            .query((rs, i) ->
                new Stats(
                    rs.getLong(1),
                    rs.getLong(2),
                    rs.getLong(3),
                    rs.getLong(4),
                    rs.getLong(5),
                    rs.getLong(6),
                    rs.getLong(7),
                    rs.getLong(8),
                    rs.getLong(9)))
            .single();
    return jdbc.sql("select * from comms_broadcast where id = ?")
        .param(id)
        .query((rs, i) -> map(rs, stats))
        .optional()
        .orElseThrow(() -> ApiException.notFound("Broadcast"));
  }

  private static Broadcast map(ResultSet rs, Stats stats) throws SQLException {
    return new Broadcast(
        rs.getObject("id", UUID.class),
        rs.getString("title"),
        rs.getString("details"),
        rs.getString("template_name"),
        List.of((String[]) rs.getArray("channels").getArray()),
        List.of((String[]) rs.getArray("audience_tags").getArray()),
        rs.getBoolean("ack_required"),
        rs.getString("consent_question"),
        rs.getBoolean("emergency"),
        rs.getString("status"),
        ts(rs.getTimestamp("send_at")),
        ts(rs.getTimestamp("sent_at")),
        rs.getTimestamp("created_at").toInstant(),
        stats);
  }

  static String mask(String address) {
    if (address == null) {
      return null;
    }
    if (address.contains("@")) {
      int at = address.indexOf('@');
      return address.charAt(0) + "***" + address.substring(at);
    }
    String d = address.replaceAll("\\D", "");
    return d.length() <= 4 ? "****" : "******" + d.substring(d.length() - 4);
  }

  private static Instant ts(Timestamp t) {
    return t == null ? null : t.toInstant();
  }

  private static Timestamp ts(Instant i) {
    return i == null ? null : Timestamp.from(i);
  }
}
