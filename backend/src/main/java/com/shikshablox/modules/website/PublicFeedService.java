package com.shikshablox.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.TenantContext;
import com.shikshablox.platform.tenancy.TenantResolver;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Public read model for content-type feeds: published items only, consent-checked media. */
@Service
class PublicFeedService {

  private final TenantResolver resolver;
  private final ContentService content;
  private final PublicSiteService site;
  private final ObjectMapper json;

  PublicFeedService(
      TenantResolver resolver, ContentService content, PublicSiteService site, ObjectMapper json) {
    this.resolver = resolver;
    this.content = content;
    this.site = site;
    this.json = json;
  }

  Optional<ObjectNode> feed(String host, String type, int limit, String show, String department) {
    if (!ContentService.TYPES.containsKey(type)) {
      return Optional.empty();
    }
    Optional<Organization> org = resolver.resolve(host);
    return org.map(o ->
        TenantContext.callInTenant(
            o.id(), () -> render(type, content.feed(type, limit, show, department), false)));
  }

  Optional<ObjectNode> item(String host, String type, String slug) {
    if (!ContentService.TYPES.containsKey(type) || !ContentService.TYPES.get(type).slugged()) {
      return Optional.empty();
    }
    Optional<Organization> org = resolver.resolve(host);
    if (org.isEmpty()) {
      return Optional.empty();
    }
    return TenantContext.callInTenant(
        org.get().id(),
        () -> content.publishedBySlug(type, slug).map(i -> render(type, List.of(i), true)));
  }

  private ObjectNode render(String type, List<ContentService.Item> items, boolean detail) {
    ContentService.TypeSpec spec = ContentService.spec(type);
    ObjectNode out = json.createObjectNode();
    ArrayNode arr = out.putArray("items");
    Set<UUID> media = new LinkedHashSet<>();
    for (ContentService.Item it : items) {
      JsonNode d = it.data();
      ObjectNode o = arr.addObject();
      o.put("id", it.id().toString());
      if (it.slug() != null) {
        o.put("slug", it.slug());
      }
      for (String k :
          List.of(
              "title",
              "summary",
              "date",
              "endDate",
              "location",
              "category",
              "role",
              "department",
              "qualification",
              "imageId",
              "attachmentId")) {
        if (d.hasNonNull(k)) {
          o.set(k, d.get(k));
        }
      }
      if (d.hasNonNull("pinned")) {
        o.put("pinned", d.get("pinned").asBoolean());
      }
      // Story bodies only on detail pages (and careers, which expand in place).
      if (d.hasNonNull("bodyHtml") && (detail || "careers".equals(type))) {
        o.set("bodyHtml", d.get("bodyHtml"));
      }
      if (d.has("imageIds")) {
        ArrayNode ids = (ArrayNode) d.get("imageIds");
        o.put("count", ids.size());
        if (!o.has("imageId") && !ids.isEmpty()) {
          o.put("imageId", ids.get(0).asText());
        }
        if (detail) {
          o.set("imageIds", ids);
        }
      }
      Set<UUID> ids = detail ? ContentService.mediaIds(spec, d) : new LinkedHashSet<>();
      if (o.hasNonNull("imageId")) {
        ids.add(UUID.fromString(o.get("imageId").asText()));
      }
      if (o.hasNonNull("attachmentId")) {
        ids.add(UUID.fromString(o.get("attachmentId").asText()));
      }
      media.addAll(ids);
    }
    site.addMedia(media, out.putObject("media"), true);
    return out;
  }
}
