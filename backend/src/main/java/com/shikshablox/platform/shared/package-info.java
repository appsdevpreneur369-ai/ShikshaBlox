/**
 * Shared kernel: cross-cutting web concerns (request id, error model) available to every module.
 */
@org.springframework.modulith.ApplicationModule(
    id = "shared",
    displayName = "Shared Kernel",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.shikshablox.platform.shared;
