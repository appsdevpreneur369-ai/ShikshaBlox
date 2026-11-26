package com.shikshablox.modules.website;

import java.util.UUID;

/**
 * Published by any module whose change shows on the public site (e.g. the admissions form being
 * opened), so the site's cache for the school is refreshed after the transaction commits.
 */
public record SiteChanged(UUID organizationId) {}
