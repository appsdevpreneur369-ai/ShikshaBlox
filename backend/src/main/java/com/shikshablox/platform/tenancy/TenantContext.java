package com.shikshablox.platform.tenancy;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The tenant (organization), user and system flag for the current unit of work.
 *
 * <p>Every database connection handed out by the application is bound to the context that is
 * current when the connection is checked out (see {@link TenantAwareDataSource}), which is what
 * PostgreSQL Row-Level Security evaluates. Therefore a context must be opened <em>before</em> a
 * transaction starts; changing it inside a transaction is rejected.
 *
 * <p>The tenant is always derived server-side (host name or authenticated principal), never from a
 * value supplied by the browser.
 */
public final class TenantContext {

  private static final Logger log = LoggerFactory.getLogger(TenantContext.class);
  public static final String MDC_TENANT = "tenantId";

  /** Immutable view of the current context. */
  public record Snapshot(UUID organizationId, UUID userId, boolean system) {
    static final Snapshot EMPTY = new Snapshot(null, null, false);
  }

  /** Restores the previous context when closed. */
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }

  private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

  private TenantContext() {}

  public static Snapshot current() {
    Snapshot s = CURRENT.get();
    return s == null ? Snapshot.EMPTY : s;
  }

  public static Optional<UUID> organizationId() {
    return Optional.ofNullable(current().organizationId());
  }

  public static Optional<UUID> userId() {
    return Optional.ofNullable(current().userId());
  }

  /** The current tenant, or an exception if the code path runs without one. */
  public static UUID requireOrganizationId() {
    return organizationId()
        .orElseThrow(() -> new IllegalStateException("No tenant context for this operation"));
  }

  /** A request on behalf of {@code userId} (may be null) inside organization {@code orgId}. */
  public static Scope forTenant(UUID orgId, UUID userId) {
    return open(new Snapshot(orgId, userId, false));
  }

  /** A signed-in user acting outside any school (e.g. a platform administrator). */
  public static Scope forUser(UUID userId) {
    return open(new Snapshot(null, userId, false));
  }

  /**
   * Explicit system context that may read across tenants (authentication, onboarding, scheduled
   * jobs). Every use is logged with its reason; never use it to serve tenant data to a user.
   */
  public static Scope system(String reason) {
    log.debug("Entering system context: {}", reason);
    Snapshot prev = current();
    return open(new Snapshot(prev.organizationId(), prev.userId(), true));
  }

  public static <T> T callAsSystem(String reason, Supplier<T> work) {
    try (Scope ignored = system(reason)) {
      return work.get();
    }
  }

  public static void runAsSystem(String reason, Runnable work) {
    try (Scope ignored = system(reason)) {
      work.run();
    }
  }

  public static <T> T callInTenant(UUID orgId, Supplier<T> work) {
    try (Scope ignored = forTenant(orgId, current().userId())) {
      return work.get();
    }
  }

  private static Scope open(Snapshot next) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "Tenant context must be set before a transaction starts, not inside it");
    }
    Snapshot previous = CURRENT.get();
    String previousMdc = MDC.get(MDC_TENANT);
    CURRENT.set(next);
    if (next.organizationId() != null) {
      MDC.put(MDC_TENANT, next.organizationId().toString());
    }
    return () -> {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
      if (previousMdc == null) {
        MDC.remove(MDC_TENANT);
      } else {
        MDC.put(MDC_TENANT, previousMdc);
      }
    };
  }
}
