package com.shikshablox.platform.tenancy;

import com.shikshablox.platform.shared.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TenancyController {

  record ResolvedSite(String slug, String name) {}

  record OrganizationView(UUID id, String slug, String name, String status) {
    static OrganizationView of(Organization o) {
      return new OrganizationView(o.id(), o.slug(), o.name(), o.status());
    }
  }

  record CreateOrganizationRequest(
      @NotBlank @Size(max = 63) String slug, @NotBlank @Size(min = 2, max = 200) String name) {}

  private final TenantResolver resolver;
  private final OrganizationService organizations;

  TenancyController(TenantResolver resolver, OrganizationService organizations) {
    this.resolver = resolver;
    this.organizations = organizations;
  }

  /** Used by the public site server to find the school for the host it was asked to render. */
  @GetMapping("/api/public/v1/sites/resolve")
  @Tag(name = "Public site")
  @Operation(operationId = "resolveSite", summary = "Find the school that serves a host name")
  ResolvedSite resolve(@RequestParam String host) {
    return resolver
        .resolve(host)
        .map(o -> new ResolvedSite(o.slug(), o.name()))
        .orElseThrow(() -> ApiException.notFound("School"));
  }

  @GetMapping("/api/platform/v1/organizations")
  @Tag(name = "Platform")
  @SecurityRequirement(name = "bearer")
  @PreAuthorize("hasAuthority('platform:manage')")
  @Operation(operationId = "listOrganizations", summary = "List all schools")
  List<OrganizationView> list() {
    return organizations.listAll().stream().map(OrganizationView::of).toList();
  }

  @PostMapping("/api/platform/v1/organizations")
  @ResponseStatus(HttpStatus.CREATED)
  @Tag(name = "Platform")
  @SecurityRequirement(name = "bearer")
  @PreAuthorize("hasAuthority('platform:manage')")
  @Operation(operationId = "createOrganization", summary = "Create a school")
  OrganizationView create(@Valid @RequestBody CreateOrganizationRequest req) {
    return OrganizationView.of(organizations.create(req.slug(), req.name()));
  }
}
