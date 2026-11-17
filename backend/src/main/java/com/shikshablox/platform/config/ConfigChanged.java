package com.shikshablox.platform.config;

import java.util.UUID;

/** Published inside the writing transaction whenever a configuration value changes. */
public record ConfigChanged(
    UUID organizationId, String scope, String key, Object before, Object after) {}
