package com.shikshablox.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Studio API for content types: notices, news, events, albums, staff, FAQs, careers, disclosures.
 */
@RestController
@RequestMapping("/api/admin/v1/content")
@Tag(name = "Content")
@SecurityRequirement(name = "bearer")
class ContentAdminController {

  record FieldView(String name, String kind, boolean required, int max, String label) {}

  record TypeView(String type, String label, List<FieldView> fields, boolean slugged) {}

  record SaveRequest(@NotNull JsonNode data, Boolean publish) {}

  record ReorderRequest(@NotNull @Size(max = 500) List<UUID> ids) {}

  private final ContentService content;

  ContentAdminController(ContentService content) {
    this.content = content;
  }

  @GetMapping("/types")
  @PreAuthorize("hasAuthority('content:view')")
  @Operation(operationId = "contentTypes", summary = "Content types and their fields")
  List<TypeView> types() {
    return List.of("notices", "news", "events", "albums", "staff", "faqs", "careers", "disclosures")
        .stream()
        .map(ContentService::spec)
        .map(s ->
            new TypeView(
                s.type(),
                s.label(),
                s.fields().stream()
                    .map(f ->
                        new FieldView(f.name(), f.kind().name(), f.required(), f.max(), f.label()))
                    .toList(),
                s.slugged()))
        .toList();
  }

  @GetMapping("/{type}")
  @PreAuthorize("hasAuthority('content:view')")
  @Operation(operationId = "listContent", summary = "Items of a content type")
  List<ContentService.Item> list(@PathVariable String type) {
    return content.list(type);
  }

  @GetMapping("/{type}/{id}")
  @PreAuthorize("hasAuthority('content:view')")
  @Operation(operationId = "getContent", summary = "One item")
  ContentService.Item get(@PathVariable String type, @PathVariable UUID id) {
    return content.get(type, id);
  }

  @PostMapping("/{type}")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('content:edit')")
  @Operation(
      operationId = "createContent",
      summary = "Create an item (publishing needs publish rights)")
  ContentService.Item create(@PathVariable String type, @RequestBody SaveRequest req) {
    boolean publish = Boolean.TRUE.equals(req.publish()) && canPublish();
    return content.create(type, req.data(), publish);
  }

  @PutMapping("/{type}/{id}")
  @PreAuthorize("hasAuthority('content:edit')")
  @Operation(operationId = "updateContent", summary = "Update an item")
  ContentService.Item update(
      @PathVariable String type, @PathVariable UUID id, @RequestBody SaveRequest req) {
    return content.update(type, id, req.data());
  }

  @PostMapping("/{type}/{id}/publish")
  @PreAuthorize("hasAuthority('content:publish')")
  @Operation(operationId = "publishContent", summary = "Publish an item")
  ContentService.Item publish(@PathVariable String type, @PathVariable UUID id) {
    return content.setPublished(type, id, true);
  }

  @PostMapping("/{type}/{id}/unpublish")
  @PreAuthorize("hasAuthority('content:publish')")
  @Operation(operationId = "unpublishContent", summary = "Unpublish an item")
  ContentService.Item unpublish(@PathVariable String type, @PathVariable UUID id) {
    return content.setPublished(type, id, false);
  }

  @PutMapping("/{type}/order")
  @PreAuthorize("hasAuthority('content:edit')")
  @Operation(operationId = "reorderContent", summary = "Set the display order")
  void reorder(@PathVariable String type, @RequestBody ReorderRequest req) {
    content.reorder(type, req.ids());
  }

  @DeleteMapping("/{type}/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('content:publish')")
  @Operation(operationId = "deleteContent", summary = "Delete an item")
  void delete(@PathVariable String type, @PathVariable UUID id) {
    content.delete(type, id);
  }

  private static boolean canPublish() {
    return com.shikshablox.platform.identity.CurrentUser.get()
        .map(u ->
            u.roles().stream()
                .anyMatch(r ->
                    r.permissions()
                        .contains(com.shikshablox.platform.identity.Permission.CONTENT_PUBLISH)))
        .orElse(false);
  }
}
