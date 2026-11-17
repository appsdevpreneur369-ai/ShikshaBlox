package com.shikshablox.platform.config;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Settings")
@SecurityRequirement(name = "bearer")
class ConfigController {

  record SettingView(
      String key,
      String type,
      String description,
      boolean editable,
      JsonNode value,
      String source) {}

  record SetValueRequest(@NotNull JsonNode value) {}

  private final ConfigService config;

  ConfigController(ConfigService config) {
    this.config = config;
  }

  @GetMapping("/api/admin/v1/settings")
  @PreAuthorize("hasAuthority('content:view')")
  @Operation(
      operationId = "listSettings",
      summary = "Effective settings for the school and where each comes from")
  List<SettingView> list() {
    return config.effective().stream()
        .map(e ->
            new SettingView(
                e.key().key(),
                e.key().type().name(),
                e.key().description(),
                e.key().schoolEditable(),
                e.value(),
                e.source().name()))
        .toList();
  }

  @PutMapping("/api/admin/v1/settings/{key}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('school:manage')")
  @Operation(operationId = "updateSetting", summary = "Change a school setting")
  void update(@PathVariable String key, @Valid @RequestBody SetValueRequest req) {
    config.setForSchool(key, req.value());
  }

  @PutMapping("/api/platform/v1/settings/{scope}/{key}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('platform:manage')")
  @Operation(
      operationId = "updatePlatformSetting",
      summary = "Change a platform or profile default")
  void updatePlatform(
      @PathVariable ConfigService.Scope scope,
      @PathVariable String key,
      @RequestParam(required = false) String profile,
      @Valid @RequestBody SetValueRequest req) {
    config.setPlatformDefault(scope, profile, key, req.value());
  }
}
