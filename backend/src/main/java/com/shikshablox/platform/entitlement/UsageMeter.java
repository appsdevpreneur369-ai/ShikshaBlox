package com.shikshablox.platform.entitlement;

import java.util.UUID;

/**
 * Reports current usage of one plan limit (e.g. {@code storage_bytes}, {@code custom_pages}).
 * Implemented by the module that owns the data, so entitlement never reads other modules' tables.
 */
public interface UsageMeter {

  /** Module code the limit belongs to, e.g. {@code WEBSITE}. */
  String module();

  /** Limit key, e.g. {@code storage_bytes}. */
  String limitKey();

  /** Current usage for the organization; called inside that organization's tenant context. */
  long usage(UUID organizationId);
}
