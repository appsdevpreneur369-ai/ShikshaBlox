package com.shikshablox.platform.entitlement;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.OrganizationCreated;
import com.shikshablox.platform.tenancy.TenantContext;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Server-side plan enforcement: which modules and features a school has, and how close it is to
 * each limit (warn at 80%, block at 100%).
 */
@Service
public class EntitlementService {

  public static final String DEFAULT_PLAN = "WEB_FREE";
  public static final double WARN_AT = 0.8;
  public static final long UNLIMITED = -1;

  public enum Depth {
    LITE,
    STANDARD,
    ADVANCED
  }

  public enum LimitState {
    OK,
    WARNING,
    BLOCKED
  }

  public record Entitlement(
      String module,
      String planCode,
      Depth depth,
      Set<String> features,
      Map<String, Long> limits,
      Instant expiresAt) {

    boolean active(Instant now) {
      return expiresAt == null || expiresAt.isAfter(now);
    }
  }

  /** Usage of one limit; {@code limit = -1} means unlimited. */
  public record LimitStatus(String module, String key, long limit, long used, LimitState state) {
    public Integer percent() {
      return limit <= 0 ? null : (int) Math.min(100, Math.round(used * 100.0 / limit));
    }
  }

  public record Plan(String code, String name) {}

  private record PlanEntry(
      String module, String depth, List<String> features, Map<String, Long> limits) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final ObjectMapper json;
  private final Map<String, UsageMeter> meters;
  private final AuditService audit;

  EntitlementService(
      JdbcClient jdbc,
      TransactionTemplate tx,
      ObjectMapper json,
      List<UsageMeter> meters,
      AuditService audit) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.json = json;
    this.audit = audit;
    this.meters =
        meters.stream().collect(Collectors.toMap(m -> m.module() + ":" + m.limitKey(), m -> m));
  }

  // ----------------------------------------------------------------------------- plans

  public List<Plan> plans() {
    return jdbc.sql("select code, name from plan order by sort_order")
        .query((rs, i) -> new Plan(rs.getString("code"), rs.getString("name")))
        .list();
  }

  /** New schools start on the free plan (runs inside the creating transaction). */
  @EventListener
  void onOrganizationCreated(OrganizationCreated event) {
    writePlan(event.organizationId(), DEFAULT_PLAN);
  }

  /** Replaces a school's entitlements with a plan's bundle. Platform operation. */
  public void applyPlan(UUID organizationId, String planCode) {
    TenantContext.runAsSystem(
        "apply plan",
        () ->
            tx.executeWithoutResult(s -> {
              List<Entitlement> before = load(organizationId);
              writePlan(organizationId, planCode);
              audit.recordFor(
                  organizationId,
                  "entitlement.plan_changed",
                  "organization",
                  organizationId.toString(),
                  Map.of("plans", before.stream().map(Entitlement::planCode).distinct().toList()),
                  Map.of("plan", planCode));
            }));
  }

  private void writePlan(UUID organizationId, String planCode) {
    String raw =
        jdbc.sql("select entitlements::text from plan where code = ?")
            .param(planCode)
            .query(String.class)
            .optional()
            .orElseThrow(() -> ApiException.badRequest("unknown_plan", "Unknown plan."));
    List<PlanEntry> entries;
    try {
      entries = json.readValue(raw, new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Invalid plan definition " + planCode, e);
    }
    jdbc.sql("delete from entitlement where organization_id = ?").param(organizationId).update();
    for (PlanEntry e : entries) {
      jdbc.sql(
              "insert into entitlement (organization_id, module_code, plan_code, depth, features,"
                  + " limits) values (?, ?, ?, ?, ?, ?::jsonb)")
          .params(
              organizationId,
              e.module(),
              planCode,
              e.depth(),
              e.features().toArray(String[]::new),
              toJson(e.limits()))
          .update();
    }
  }

  // ----------------------------------------------------------------------------- queries

  /** Entitlements of the current school. */
  public List<Entitlement> current() {
    return load(TenantContext.requireOrganizationId());
  }

  private List<Entitlement> load(UUID organizationId) {
    Instant now = Instant.now();
    return jdbc
        .sql(
            "select module_code, plan_code, depth, features, limits::text, expires_at"
                + " from entitlement where organization_id = ? order by module_code")
        .param(organizationId)
        .query(this::map)
        .list()
        .stream()
        .filter(e -> e.active(now))
        .toList();
  }

  public Optional<Entitlement> module(String module) {
    return current().stream().filter(e -> e.module().equals(module)).findFirst();
  }

  /** The plan code of the current school's website module (the headline plan). */
  public Optional<String> currentPlan() {
    return module("WEBSITE").map(Entitlement::planCode);
  }

  public boolean hasModule(String module) {
    return module(module).isPresent();
  }

  public boolean hasFeature(String module, String feature) {
    return module(module).map(e -> e.features().contains(feature)).orElse(false);
  }

  public void requireModule(String module) {
    if (!hasModule(module)) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "module_not_in_plan", "This feature is not part of your plan.");
    }
  }

  public void requireFeature(String module, String feature) {
    if (!hasFeature(module, feature)) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "feature_not_in_plan",
          "This feature is available on Web Pro. Ask your administrator to upgrade.");
    }
  }

  /** The configured limit (-1 = unlimited, 0 = not allowed). Missing limits count as 0. */
  public long limit(String module, String key) {
    return module(module).map(e -> e.limits().getOrDefault(key, 0L)).orElse(0L);
  }

  public LimitStatus status(String module, String key) {
    long limit = limit(module, key);
    UsageMeter meter = meters.get(module + ":" + key);
    long used = meter == null ? 0 : meter.usage(TenantContext.requireOrganizationId());
    return new LimitStatus(module, key, limit, used, limitState(limit, used));
  }

  /** Every limit of every module with current usage, for the plan & usage screen. */
  public List<LimitStatus> allStatuses() {
    return current().stream()
        .flatMap(e -> e.limits().keySet().stream().map(k -> status(e.module(), k)))
        .toList();
  }

  /**
   * Fails gracefully when adding {@code amount} would exceed the limit. Call before creating pages,
   * uploading files, adding languages or domains.
   */
  public void checkCanAdd(String module, String key, long amount, String friendlyName) {
    long limit = limit(module, key);
    if (limit == UNLIMITED) {
      return;
    }
    LimitStatus s = status(module, key);
    if (s.used() + amount > limit) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "limit_reached",
          limit == 0
              ? "Your plan does not include " + friendlyName + ". Upgrade to Web Pro to unlock it."
              : "You have reached your plan's limit for "
                  + friendlyName
                  + ". Remove something or upgrade to Web Pro.");
    }
  }

  public static LimitState limitState(long limit, long used) {
    if (limit == UNLIMITED) {
      return LimitState.OK;
    }
    if (used >= limit) {
      return LimitState.BLOCKED; // includes limit 0 = not in plan
    }
    return used >= limit * WARN_AT ? LimitState.WARNING : LimitState.OK;
  }

  // ----------------------------------------------------------------------------- mapping

  private Entitlement map(ResultSet rs, int row) throws SQLException {
    Array features = rs.getArray("features");
    Timestamp expires = rs.getTimestamp("expires_at");
    Map<String, Long> limits;
    try {
      limits =
          json.readValue(
              rs.getString("limits"), new TypeReference<LinkedHashMap<String, Long>>() {});
    } catch (Exception e) {
      throw new SQLException("Invalid limits json", e);
    }
    return new Entitlement(
        rs.getString("module_code"),
        rs.getString("plan_code"),
        Depth.valueOf(rs.getString("depth")),
        Set.copyOf(Arrays.asList((String[]) features.getArray())),
        limits,
        expires == null ? null : expires.toInstant());
  }

  private String toJson(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
