package com.shikshablox.platform.tenancy;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Resolves a public host name to a school: {@code <slug>.<base-domain>} or a verified custom
 * domain. Anonymous lookups go through narrow SECURITY DEFINER SQL functions, never a blanket RLS
 * bypass.
 */
@Component
public class TenantResolver {

  private static final Pattern HOST =
      Pattern.compile(
          "^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*$");

  private final JdbcClient jdbc;
  private final OrganizationService organizations;
  private final String baseDomain;

  TenantResolver(
      JdbcClient jdbc,
      OrganizationService organizations,
      @Value("${shikshablox.base-domain}") String baseDomain) {
    this.jdbc = jdbc;
    this.organizations = organizations;
    this.baseDomain = baseDomain.toLowerCase(Locale.ROOT);
  }

  /** Lower-cases, strips port and trailing dot; empty if the value is not a valid host name. */
  public static Optional<String> normaliseHost(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String host = raw.trim().toLowerCase(Locale.ROOT);
    int colon = host.indexOf(':');
    if (colon >= 0) {
      host = host.substring(0, colon);
    }
    if (host.endsWith(".")) {
      host = host.substring(0, host.length() - 1);
    }
    return HOST.matcher(host).matches() ? Optional.of(host) : Optional.empty();
  }

  public Optional<UUID> resolveOrganizationId(String rawHost) {
    Optional<String> host = normaliseHost(rawHost);
    if (host.isEmpty()) {
      return Optional.empty();
    }
    String h = host.get();
    String suffix = "." + baseDomain;
    if (h.endsWith(suffix)) {
      String sub = h.substring(0, h.length() - suffix.length());
      if (sub.contains(".") || OrganizationService.RESERVED_SLUGS.contains(sub)) {
        return Optional.empty();
      }
      return jdbc.sql("select r from sb_resolve_slug(?) r where r is not null")
          .param(sub)
          .query(UUID.class)
          .optional();
    }
    return jdbc.sql("select r from sb_resolve_domain(?) r where r is not null")
        .param(h)
        .query(UUID.class)
        .optional();
  }

  /** The active school serving {@code rawHost}, read inside that school's tenant context. */
  public Optional<Organization> resolve(String rawHost) {
    return resolveOrganizationId(rawHost)
        .flatMap(id -> TenantContext.callInTenant(id, () -> organizations.findVisible(id)));
  }
}
