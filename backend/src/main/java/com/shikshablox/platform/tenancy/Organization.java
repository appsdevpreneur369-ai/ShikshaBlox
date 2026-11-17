package com.shikshablox.platform.tenancy;

import java.time.Instant;
import java.util.UUID;

/** A school (tenant). Single default campus for now; multi-campus is out of scope for M01. */
public record Organization(UUID id, String slug, String name, String status, Instant createdAt) {

  public boolean active() {
    return "ACTIVE".equals(status);
  }
}
