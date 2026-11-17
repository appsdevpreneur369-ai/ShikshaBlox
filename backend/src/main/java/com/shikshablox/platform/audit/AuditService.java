package com.shikshablox.platform.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shikshablox.platform.config.ConfigChanged;
import com.shikshablox.platform.identity.SecurityEvent;
import com.shikshablox.platform.shared.web.RequestIdFilter;
import com.shikshablox.platform.tenancy.OrganizationCreated;
import com.shikshablox.platform.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Append-only audit trail: who did what, when, with before/after state. Writes join the caller's
 * transaction, so an audited change and its audit record commit or roll back together.
 */
@Service
public class AuditService {

  public record Entry(
      long id,
      UUID organizationId,
      UUID actorUserId,
      String action,
      String entityType,
      String entityId,
      String before,
      String after,
      String ip,
      String requestId,
      Instant occurredAt) {}

  public record Query(
      String action,
      String entityType,
      String entityId,
      Instant from,
      Instant to,
      int limit,
      Long beforeId) {}

  private final JdbcClient jdbc;
  private final ObjectMapper json;

  AuditService(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /** Records an action in the current tenant (or platform level when there is none). */
  public void record(
      String action, String entityType, String entityId, Object before, Object after) {
    recordFor(
        TenantContext.organizationId().orElse(null), action, entityType, entityId, before, after);
  }

  /** Records an action for an explicit organization (platform operations on a school). */
  public void recordFor(
      UUID organizationId,
      String action,
      String entityType,
      String entityId,
      Object before,
      Object after) {
    insert(
        organizationId,
        TenantContext.userId().orElse(null),
        action,
        entityType,
        entityId,
        before,
        after,
        clientIp());
  }

  private void insert(
      UUID organizationId,
      UUID actor,
      String action,
      String entityType,
      String entityId,
      Object before,
      Object after,
      String ip) {
    jdbc.sql(
            "insert into audit_log (organization_id, actor_user_id, action, entity_type, entity_id,"
                + " before_state, after_state, ip, request_id)"
                + " values (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)")
        .params(
            organizationId,
            actor,
            action,
            entityType,
            entityId,
            toJson(before),
            toJson(after),
            ip,
            MDC.get(RequestIdFilter.MDC_KEY))
        .update();
  }

  // ------------------------------------------------------------------- events from lower modules

  @EventListener
  void onSecurityEvent(SecurityEvent e) {
    insert(
        e.organizationId(),
        e.userId(),
        e.type(),
        "user",
        e.userId() == null ? null : e.userId().toString(),
        null,
        Map.of("success", e.success()),
        e.ip());
  }

  @EventListener
  void onOrganizationCreated(OrganizationCreated e) {
    recordFor(
        e.organizationId(),
        "organization.created",
        "organization",
        e.organizationId().toString(),
        null,
        Map.of("slug", e.slug(), "name", e.name()));
  }

  @EventListener
  void onConfigChanged(ConfigChanged e) {
    recordFor(
        e.organizationId(),
        "config.changed",
        "config",
        e.scope() + ":" + e.key(),
        e.before(),
        e.after());
  }

  // ------------------------------------------------------------------- queries

  /** Audit entries visible in the current context (RLS: own school, or all for the platform). */
  public List<Entry> search(Query q) {
    StringBuilder sql =
        new StringBuilder(
            "select id, organization_id, actor_user_id, action, entity_type, entity_id,"
                + " before_state::text, after_state::text, ip, request_id, occurred_at"
                + " from audit_log where true");
    var params = new java.util.ArrayList<Object>();
    if (q.action() != null && !q.action().isBlank()) {
      sql.append(" and action like ?");
      params.add(q.action().replace("%", "") + "%");
    }
    if (q.entityType() != null && !q.entityType().isBlank()) {
      sql.append(" and entity_type = ?");
      params.add(q.entityType());
    }
    if (q.entityId() != null && !q.entityId().isBlank()) {
      sql.append(" and entity_id = ?");
      params.add(q.entityId());
    }
    if (q.from() != null) {
      sql.append(" and occurred_at >= ?");
      params.add(Timestamp.from(q.from()));
    }
    if (q.to() != null) {
      sql.append(" and occurred_at < ?");
      params.add(Timestamp.from(q.to()));
    }
    if (q.beforeId() != null) {
      sql.append(" and id < ?");
      params.add(q.beforeId());
    }
    sql.append(" order by id desc limit ?");
    params.add(Math.max(1, Math.min(q.limit(), 200)));
    return jdbc.sql(sql.toString())
        .params(params)
        .query((rs, i) ->
            new Entry(
                rs.getLong("id"),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("actor_user_id", UUID.class),
                rs.getString("action"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getString("before_state"),
                rs.getString("after_state"),
                rs.getString("ip"),
                rs.getString("request_id"),
                rs.getTimestamp("occurred_at").toInstant()))
        .list();
  }

  private String toJson(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("Audit state could not be serialised", e);
    }
  }

  private static String clientIp() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      HttpServletRequest req = attrs.getRequest();
      return req.getRemoteAddr();
    }
    return null;
  }
}
