package com.shikshablox.platform.tenancy;

import java.util.UUID;

/** Published inside the creating transaction when a school is created. */
public record OrganizationCreated(UUID organizationId, String slug, String name) {}
