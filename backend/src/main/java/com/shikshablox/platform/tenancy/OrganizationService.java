package com.shikshablox.platform.tenancy;

import com.shikshablox.platform.shared.web.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Creates and reads organizations (tenants). */
@Service
public class OrganizationService {

  private static final Pattern SLUG = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");

  /** Subdomains that can never be a school's slug. */
  public static final Set<String> RESERVED_SLUGS =
      Set.of(
          "www",
          "admin",
          "api",
          "media",
          "mail",
          "app",
          "static",
          "cdn",
          "assets",
          "status",
          "help",
          "support",
          "docs",
          "blog",
          "staging",
          "platform",
          "studio",
          "shikshablox");

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final ApplicationEventPublisher events;

  OrganizationService(JdbcClient jdbc, TransactionTemplate tx, ApplicationEventPublisher events) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.events = events;
  }

  /** Creates a school with its default campus. Platform operation (system context). */
  public Organization create(String slug, String name) {
    String normalised = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
    if (!SLUG.matcher(normalised).matches() || RESERVED_SLUGS.contains(normalised)) {
      throw ApiException.badRequest(
          "invalid_slug", "Use 1-63 lowercase letters, digits or hyphens (not a reserved name).");
    }
    String cleanName = name == null ? "" : name.trim();
    if (cleanName.length() < 2 || cleanName.length() > 200) {
      throw ApiException.badRequest("invalid_name", "School name must be 2-200 characters.");
    }
    return TenantContext.callAsSystem(
        "create organization",
        () ->
            tx.execute(status -> {
              boolean exists =
                  jdbc.sql("select exists(select 1 from organization where slug = ?)")
                      .param(normalised)
                      .query(Boolean.class)
                      .single();
              if (exists) {
                throw ApiException.conflict("slug_taken", "That web address is already used.");
              }
              Organization org =
                  jdbc.sql(
                          "insert into organization (slug, name) values (?, ?)"
                              + " returning id, slug, name, status, config_profile, created_at")
                      .params(normalised, cleanName)
                      .query(OrganizationService::map)
                      .single();
              jdbc.sql(
                      "insert into campus (organization_id, name, is_default)"
                          + " values (?, ?, true)")
                  .params(org.id(), "Main campus")
                  .update();
              // Listeners (default plan, audit) run in this transaction and system context.
              events.publishEvent(new OrganizationCreated(org.id(), org.slug(), org.name()));
              return org;
            }));
  }

  /** The current tenant's organization (RLS-scoped). */
  public Optional<Organization> current() {
    return TenantContext.organizationId().flatMap(this::findVisible);
  }

  /** Reads an organization visible in the current context. */
  public Optional<Organization> findVisible(UUID id) {
    return jdbc.sql(
            "select id, slug, name, status, config_profile, created_at from organization where id = ?")
        .param(id)
        .query(OrganizationService::map)
        .optional();
  }

  /**
   * Organizations by id across tenants, for showing a signed-in user the schools they belong to.
   * Callers must only pass ids the user is a member of.
   */
  public List<Organization> findByIds(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return TenantContext.callAsSystem(
        "list organizations of a member",
        () ->
            jdbc.sql(
                    "select id, slug, name, status, config_profile, created_at from organization"
                        + " where id = any(?) order by name")
                .param(ids.toArray(UUID[]::new))
                .query(OrganizationService::map)
                .list());
  }

  /** All organizations (platform administration). */
  public List<Organization> listAll() {
    return TenantContext.callAsSystem(
        "platform organization list",
        () ->
            jdbc.sql(
                    "select id, slug, name, status, config_profile, created_at from organization order by name")
                .query(OrganizationService::map)
                .list());
  }

  public Optional<Organization> findBySlug(String slug) {
    return TenantContext.callAsSystem(
        "find organization by slug",
        () ->
            jdbc.sql(
                    "select id, slug, name, status, config_profile, created_at from organization where slug = ?")
                .param(slug.toLowerCase(Locale.ROOT))
                .query(OrganizationService::map)
                .optional());
  }

  /**
   * Registers an already-verified host for a school's public site (seeding / platform operations).
   * Custom-domain self-service with DNS verification arrives in M7.
   */
  public void addVerifiedDomain(UUID organizationId, String host) {
    String normalised = host.trim().toLowerCase(Locale.ROOT);
    TenantContext.runAsSystem(
        "register verified domain",
        () ->
            jdbc.sql(
                    "insert into site_domain (organization_id, host, status, verified_at)"
                        + " values (?, ?, 'VERIFIED', now())"
                        + " on conflict (host) do update set organization_id = excluded.organization_id,"
                        + " status = 'VERIFIED', verified_at = now()")
                .params(organizationId, normalised)
                .update());
  }

  static Organization map(ResultSet rs, int row) throws SQLException {
    return new Organization(
        rs.getObject("id", UUID.class),
        rs.getString("slug"),
        rs.getString("name"),
        rs.getString("status"),
        rs.getString("config_profile"),
        rs.getTimestamp("created_at").toInstant());
  }
}
