package com.shikshablox.platform.entitlement;

import com.shikshablox.platform.entitlement.EntitlementService.LimitStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Plan & entitlements")
@SecurityRequirement(name = "bearer")
class EntitlementController {

  record ModuleView(String module, String depth, List<String> features) {}

  record LimitView(
      String module, String key, long limit, long used, Integer percent, String state) {
    static LimitView of(LimitStatus s) {
      return new LimitView(s.module(), s.key(), s.limit(), s.used(), s.percent(), s.state().name());
    }
  }

  record EntitlementsView(
      String plan, String planName, List<ModuleView> modules, List<LimitView> limits) {}

  record PlanView(String code, String name) {}

  record ChangePlanRequest(@NotBlank String planCode) {}

  private final EntitlementService entitlements;

  EntitlementController(EntitlementService entitlements) {
    this.entitlements = entitlements;
  }

  @GetMapping("/api/admin/v1/entitlements")
  @PreAuthorize("hasAuthority('content:view')")
  @Operation(operationId = "getEntitlements", summary = "Current plan, modules and limit usage")
  EntitlementsView current() {
    String plan = entitlements.currentPlan().orElse(null);
    String planName =
        entitlements.plans().stream()
            .filter(p -> p.code().equals(plan))
            .map(EntitlementService.Plan::name)
            .findFirst()
            .orElse(null);
    return new EntitlementsView(
        plan,
        planName,
        entitlements.current().stream()
            .map(e ->
                new ModuleView(
                    e.module(), e.depth().name(), e.features().stream().sorted().toList()))
            .toList(),
        entitlements.allStatuses().stream().map(LimitView::of).toList());
  }

  @GetMapping("/api/platform/v1/plans")
  @PreAuthorize("hasAuthority('platform:manage')")
  @Operation(operationId = "listPlans", summary = "Available plans")
  List<PlanView> plans() {
    return entitlements.plans().stream().map(p -> new PlanView(p.code(), p.name())).toList();
  }

  @PutMapping("/api/platform/v1/organizations/{organizationId}/plan")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('platform:manage')")
  @Operation(operationId = "changePlan", summary = "Move a school to another plan")
  void changePlan(@PathVariable UUID organizationId, @Valid @RequestBody ChangePlanRequest req) {
    entitlements.applyPlan(organizationId, req.planCode());
  }
}
