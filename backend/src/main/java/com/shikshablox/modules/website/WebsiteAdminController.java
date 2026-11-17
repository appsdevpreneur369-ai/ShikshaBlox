package com.shikshablox.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.shikshablox.platform.identity.CurrentUser;
import com.shikshablox.platform.identity.Permission;
import com.shikshablox.platform.identity.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1")
@Tag(name = "Website")
@SecurityRequirement(name = "bearer")
class WebsiteAdminController {

  record SiteView(
      String theme,
      JsonNode brand,
      JsonNode profile,
      UUID logoMediaId,
      List<String> languages,
      String defaultLanguage,
      boolean onboardingCompleted,
      Set<String> themes,
      Set<String> fonts,
      Set<String> radii,
      Set<String> supportedLanguages) {
    static SiteView of(SiteService.Site s) {
      return new SiteView(
          s.theme(),
          s.brand(),
          s.profile(),
          s.logoMediaId(),
          s.languages(),
          s.defaultLanguage(),
          s.onboardingCompleted(),
          SiteService.THEMES,
          SiteService.FONTS,
          SiteService.RADII,
          SiteService.SUPPORTED_LANGUAGES);
    }
  }

  record DesignRequest(@NotBlank String theme, @NotNull JsonNode brand, UUID logoMediaId) {}

  record ProfileRequest(@NotNull JsonNode profile) {}

  record LanguagesRequest(@NotNull @Size(min = 1, max = 5) List<String> languages) {}

  record OnboardingRequest(
      @NotBlank String theme,
      @NotNull JsonNode brand,
      @NotNull JsonNode profile,
      UUID logoMediaId) {}

  record CreatePageRequest(
      @NotBlank @Size(max = 120) String slug, @NotBlank @Size(max = 160) String title) {}

  record SavePageRequest(
      @NotBlank @Size(max = 160) String title, JsonNode seo, @NotNull JsonNode document) {}

  record PublishRequest(@Size(max = 300) String note) {}

  record ReusableRequest(@NotBlank @Size(max = 120) String name, @NotNull JsonNode block) {}

  private static final String LANG = "^[a-z]{2}(-[A-Z]{2})?$";

  private final SiteService sites;
  private final PageService pages;
  private final NavigationService navigation;
  private final OnboardingService onboarding;

  WebsiteAdminController(
      SiteService sites,
      PageService pages,
      NavigationService navigation,
      OnboardingService onboarding) {
    this.sites = sites;
    this.pages = pages;
    this.navigation = navigation;
    this.onboarding = onboarding;
  }

  private static boolean canUseRawHtml() {
    return CurrentUser.get()
        .map(u ->
            u.roles().stream().anyMatch(r -> r.permissions().contains(Permission.SCHOOL_MANAGE))
                || u.roles().contains(Role.PLATFORM_SUPER_ADMIN))
        .orElse(false);
  }

  // ----------------------------------------------------------------------------- site

  @GetMapping("/site")
  @PreAuthorize("hasAuthority('content:view')")
  @Operation(operationId = "getSite", summary = "Site design, profile and languages")
  SiteView site() {
    return SiteView.of(sites.current());
  }

  @PutMapping("/site/design")
  @PreAuthorize("hasAuthority('school:manage')")
  @Operation(operationId = "updateSiteDesign", summary = "Theme, colours, fonts and logo")
  SiteView design(@Valid @RequestBody DesignRequest req) {
    return SiteView.of(sites.updateDesign(req.theme(), req.brand(), req.logoMediaId()));
  }

  @PutMapping("/site/profile")
  @PreAuthorize("hasAuthority('school:manage')")
  @Operation(operationId = "updateSiteProfile", summary = "School details shown on the website")
  SiteView profile(@Valid @RequestBody ProfileRequest req) {
    return SiteView.of(sites.updateProfile(req.profile()));
  }

  @PutMapping("/site/languages")
  @PreAuthorize("hasAuthority('school:manage')")
  @Operation(
      operationId = "updateSiteLanguages",
      summary = "Website languages (first is the default)")
  SiteView languages(@Valid @RequestBody LanguagesRequest req) {
    return SiteView.of(sites.updateLanguages(req.languages()));
  }

  @PostMapping("/site/onboarding")
  @PreAuthorize("hasAuthority('school:manage')")
  @Operation(
      operationId = "completeOnboarding",
      summary = "Save the wizard and generate the website")
  SiteView onboarding(@Valid @RequestBody OnboardingRequest req) {
    return SiteView.of(
        onboarding.complete(
            new OnboardingService.OnboardingInput(
                req.theme(), req.brand(), req.profile(), req.logoMediaId())));
  }

  // ----------------------------------------------------------------------------- pages

  @GetMapping("/pages")
  @PreAuthorize("hasAuthority('content:view')")
  @Operation(operationId = "listPages", summary = "All pages with status")
  List<PageService.PageSummary> listPages(
      @RequestParam(defaultValue = "en") @Pattern(regexp = LANG) String lang) {
    return pages.list(lang);
  }

  @PostMapping("/pages")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('content:edit')")
  @Operation(operationId = "createPage", summary = "Add a custom page")
  PageService.PageDetail createPage(
      @Valid @RequestBody CreatePageRequest req,
      @RequestParam(defaultValue = "en") @Pattern(regexp = LANG) String lang) {
    return pages.createCustom(req.slug(), req.title(), lang);
  }

  @GetMapping("/pages/{id}")
  @PreAuthorize("hasAuthority('content:view')")
  @Operation(operationId = "getPage", summary = "A page with its draft and published content")
  PageService.PageDetail getPage(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "en") @Pattern(regexp = LANG) String lang) {
    return pages.get(id, lang);
  }

  @PutMapping("/pages/{id}")
  @PreAuthorize("hasAuthority('content:edit')")
  @Operation(operationId = "savePage", summary = "Save the page draft")
  PageService.PageDetail savePage(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "en") @Pattern(regexp = LANG) String lang,
      @Valid @RequestBody SavePageRequest req) {
    return pages.saveDraft(id, lang, req.title(), req.seo(), req.document(), canUseRawHtml());
  }

  @PostMapping("/pages/{id}/publish")
  @PreAuthorize("hasAuthority('content:publish')")
  @Operation(operationId = "publishPage", summary = "Publish the current draft")
  PageService.PageDetail publish(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "en") @Pattern(regexp = LANG) String lang,
      @Valid @RequestBody(required = false) PublishRequest req) {
    return pages.publish(id, lang, req == null ? null : req.note());
  }

  @PostMapping("/pages/{id}/unpublish")
  @PreAuthorize("hasAuthority('content:publish')")
  @Operation(operationId = "unpublishPage", summary = "Take a page off the website")
  PageService.PageDetail unpublish(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "en") @Pattern(regexp = LANG) String lang) {
    return pages.unpublish(id, lang);
  }

  @DeleteMapping("/pages/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('content:publish')")
  @Operation(operationId = "deletePage", summary = "Delete a custom page")
  void deletePage(@PathVariable UUID id) {
    pages.deleteCustom(id);
  }

  @GetMapping("/pages/{id}/revisions")
  @PreAuthorize("hasAuthority('content:view')")
  @Operation(operationId = "pageRevisions", summary = "Published versions of a page")
  List<PageService.Revision> revisions(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "en") @Pattern(regexp = LANG) String lang) {
    return pages.revisions(id, lang);
  }

  // ----------------------------------------------------------------------------- menus & blocks

  @GetMapping("/menus/{location}")
  @PreAuthorize("hasAuthority('content:view')")
  @Operation(operationId = "getMenu", summary = "Header or footer menu")
  JsonNode menu(@PathVariable String location) {
    return navigation.menu(location);
  }

  @PutMapping("/menus/{location}")
  @PreAuthorize("hasAuthority('content:publish')")
  @Operation(operationId = "saveMenu", summary = "Save a menu (goes live immediately)")
  JsonNode saveMenu(@PathVariable String location, @RequestBody JsonNode items) {
    return navigation.saveMenu(location, items);
  }

  @GetMapping("/reusable-blocks")
  @PreAuthorize("hasAuthority('content:view')")
  @Operation(operationId = "listReusableBlocks", summary = "Reusable blocks")
  List<NavigationService.ReusableBlock> reusable() {
    return navigation.reusableBlocks();
  }

  @PostMapping("/reusable-blocks")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('content:edit')")
  @Operation(operationId = "createReusableBlock", summary = "Save a block for reuse")
  NavigationService.ReusableBlock createReusable(@Valid @RequestBody ReusableRequest req) {
    return navigation.saveReusable(null, req.name(), req.block(), canUseRawHtml());
  }

  @PutMapping("/reusable-blocks/{id}")
  @PreAuthorize("hasAuthority('content:publish')")
  @Operation(
      operationId = "updateReusableBlock",
      summary = "Update a reusable block (changes every page using it)")
  NavigationService.ReusableBlock updateReusable(
      @PathVariable UUID id, @Valid @RequestBody ReusableRequest req) {
    return navigation.saveReusable(id, req.name(), req.block(), canUseRawHtml());
  }

  @DeleteMapping("/reusable-blocks/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('content:edit')")
  @Operation(operationId = "deleteReusableBlock", summary = "Delete an unused reusable block")
  void deleteReusable(@PathVariable UUID id) {
    navigation.deleteReusable(id);
  }
}
