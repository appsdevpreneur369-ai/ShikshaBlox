package com.shikshablox.platform.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Resolves configuration through the hierarchy PLATFORM -> PROFILE -> ORGANIZATION -> CAMPUS. */
@Service
public class ConfigService {

  public enum Scope {
    DEFAULT,
    PLATFORM,
    PROFILE,
    ORGANIZATION,
    CAMPUS
  }

  /** An effective value and the level it came from. */
  public record Effective(ConfigKey key, JsonNode value, Scope source) {}

  private record Row(String scope, String scopeRef, String key, String value) {}

  private final JdbcClient jdbc;
  private final ObjectMapper json;
  private final OrganizationService organizations;
  private final TransactionTemplate tx;
  private final ApplicationEventPublisher events;

  ConfigService(
      JdbcClient jdbc,
      ObjectMapper json,
      OrganizationService organizations,
      TransactionTemplate tx,
      ApplicationEventPublisher events) {
    this.jdbc = jdbc;
    this.json = json;
    this.organizations = organizations;
    this.tx = tx;
    this.events = events;
  }

  // ------------------------------------------------------------------------- reads

  /** All keys as seen by the current school. */
  public List<Effective> effective() {
    Organization org =
        organizations
            .current()
            .orElseThrow(
                () -> ApiException.badRequest("no_school_selected", "Choose a school first."));
    return resolve(org, null);
  }

  /** All keys for an explicit organization (system/platform callers, e.g. sign-in policy). */
  public List<Effective> effectiveFor(UUID organizationId) {
    return TenantContext.callAsSystem(
        "read configuration",
        () ->
            organizations
                .findVisible(organizationId)
                .map(o -> resolve(o, null))
                .orElseGet(() -> defaultsOnly()));
  }

  public JsonNode get(ConfigKey key) {
    return effective().stream().filter(e -> e.key() == key).findFirst().orElseThrow().value();
  }

  public boolean getBooleanFor(UUID organizationId, ConfigKey key) {
    return effectiveFor(organizationId).stream()
        .filter(e -> e.key() == key)
        .findFirst()
        .map(e -> e.value().asBoolean())
        .orElse(false);
  }

  public int getInt(ConfigKey key) {
    return get(key).asInt();
  }

  public List<String> getStringList(ConfigKey key) {
    List<String> out = new ArrayList<>();
    get(key).forEach(n -> out.add(n.asText()));
    return out;
  }

  private List<Effective> resolve(Organization org, UUID campusId) {
    List<Row> rows =
        jdbc.sql(
                "select scope, scope_ref, key, value::text from config_value where"
                    + " scope = 'PLATFORM'"
                    + " or (scope = 'PROFILE' and scope_ref = ?)"
                    + " or (scope = 'ORGANIZATION' and organization_id = ?)"
                    + " or (scope = 'CAMPUS' and organization_id = ? and scope_ref = ?)")
            .params(
                org.configProfile(),
                org.id(),
                org.id(),
                campusId == null ? "" : campusId.toString())
            .query((rs, i) ->
                new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)))
            .list();
    Map<ConfigKey, Effective> result = new EnumMap<>(ConfigKey.class);
    for (ConfigKey k : ConfigKey.values()) {
      result.put(k, new Effective(k, k.defaultValue(), Scope.DEFAULT));
    }
    for (Row r : rows) {
      ConfigKey.of(r.key())
          .ifPresent(k -> {
            Scope s = Scope.valueOf(r.scope());
            if (s.ordinal() >= result.get(k).source().ordinal()) {
              result.put(k, new Effective(k, parse(r.value()), s));
            }
          });
    }
    return List.copyOf(result.values());
  }

  private List<Effective> defaultsOnly() {
    return Arrays.stream(ConfigKey.values())
        .map(k -> new Effective(k, k.defaultValue(), Scope.DEFAULT))
        .toList();
  }

  // ------------------------------------------------------------------------- writes

  /** Sets a value for the current school (only keys a school may edit). */
  public void setForSchool(String rawKey, JsonNode value) {
    ConfigKey key = validate(rawKey, value);
    if (!key.schoolEditable()) {
      throw ApiException.forbidden();
    }
    UUID orgId = TenantContext.requireOrganizationId();
    write(Scope.ORGANIZATION, "", orgId, key, value);
  }

  /** Sets a platform-wide or profile default (platform administrators). */
  public void setPlatformDefault(Scope scope, String profile, String rawKey, JsonNode value) {
    if (scope != Scope.PLATFORM && scope != Scope.PROFILE) {
      throw ApiException.badRequest("invalid_scope", "Use PLATFORM or PROFILE.");
    }
    ConfigKey key = validate(rawKey, value);
    String ref = scope == Scope.PROFILE ? requireProfile(profile) : "";
    TenantContext.runAsSystem("platform configuration", () -> write(scope, ref, null, key, value));
  }

  private void write(Scope scope, String ref, UUID orgId, ConfigKey key, JsonNode value) {
    tx.executeWithoutResult(s -> {
      String before =
          jdbc.sql(
                  "select value::text from config_value where scope = ? and scope_ref = ?"
                      + " and organization_id is not distinct from ? and key = ?")
              .params(scope.name(), ref, orgId, key.key())
              .query(String.class)
              .optional()
              .orElse(null);
      jdbc.sql(
              "insert into config_value (scope, scope_ref, organization_id, key, value, updated_by)"
                  + " values (?, ?, ?, ?, ?::jsonb, ?)"
                  + " on conflict (scope, scope_ref, organization_id, key)"
                  + " do update set value = excluded.value, updated_by = excluded.updated_by,"
                  + " updated_at = now()")
          .params(
              scope.name(),
              ref,
              orgId,
              key.key(),
              value.toString(),
              TenantContext.userId().orElse(null))
          .update();
      events.publishEvent(
          new ConfigChanged(
              orgId, scope.name(), key.key(), before == null ? null : parse(before), value));
    });
  }

  private static ConfigKey validate(String rawKey, JsonNode value) {
    ConfigKey key =
        ConfigKey.of(rawKey)
            .orElseThrow(() -> ApiException.badRequest("unknown_key", "Unknown setting."));
    if (!key.accepts(value)) {
      throw ApiException.badRequest("invalid_value", "That value is not valid for this setting.");
    }
    return key;
  }

  private static String requireProfile(String profile) {
    if (profile == null || !profile.matches("^[a-z0-9-]{2,40}$")) {
      throw ApiException.badRequest(
          "invalid_profile", "Profile codes are 2-40 lowercase letters, digits or hyphens.");
    }
    return profile;
  }

  private JsonNode parse(String raw) {
    try {
      return json.readTree(raw);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
