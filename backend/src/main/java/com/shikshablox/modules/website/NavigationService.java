package com.shikshablox.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Header/footer menus (nested, ordered) and reusable blocks. */
@Service
public class NavigationService {

  public static final Set<String> LOCATIONS = Set.of("header", "footer");
  private static final Pattern HREF =
      Pattern.compile("^(/[^\\s]*|#[\\w-]*|mailto:[^\\s]+|tel:[+\\d\\s-]+|https://[^\\s]+)$");
  private static final Pattern LANG = Pattern.compile("^[a-z]{2}(-[A-Z]{2})?$");

  public record ReusableBlock(UUID id, String name, JsonNode block, Instant updatedAt) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final ObjectMapper json;
  private final AuditService audit;
  private final DocumentValidator validator;
  private final ApplicationEventPublisher events;

  NavigationService(
      JdbcClient jdbc,
      TransactionTemplate tx,
      ObjectMapper json,
      AuditService audit,
      DocumentValidator validator,
      ApplicationEventPublisher events) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.json = json;
    this.audit = audit;
    this.validator = validator;
    this.events = events;
  }

  // ------------------------------------------------------------------------- menus

  public JsonNode menu(String location) {
    requireLocation(location);
    return jdbc.sql("select items::text from menu where location = ?")
        .param(location)
        .query(String.class)
        .optional()
        .map(this::read)
        .orElseGet(json::createArrayNode);
  }

  public JsonNode saveMenu(String location, JsonNode items) {
    requireLocation(location);
    ArrayNode clean = cleanItems(items, 0);
    return tx.execute(s -> {
      JsonNode before = menu(location);
      writeMenu(location, clean);
      audit.record("menu.updated", "menu", location, before, clean);
      events.publishEvent(
          new PageService.PagePublished(TenantContext.requireOrganizationId(), null, null));
      return clean;
    });
  }

  void writeMenu(String location, JsonNode items) {
    jdbc.sql(
            "insert into menu (organization_id, location, items) values (?, ?, ?::jsonb)"
                + " on conflict (organization_id, location) do update set items = excluded.items, updated_at = now()")
        .params(TenantContext.requireOrganizationId(), location, items.toString())
        .update();
  }

  private ArrayNode cleanItems(JsonNode items, int depth) {
    if (items == null || !items.isArray()) {
      throw ApiException.badRequest("invalid_menu", "The menu is not valid.");
    }
    if (items.size() > 12) {
      throw ApiException.badRequest("invalid_menu", "Menus can have at most 12 items per level.");
    }
    ArrayNode out = json.createArrayNode();
    for (JsonNode it : items) {
      ObjectNode o = out.addObject();
      String id = it.path("id").asText("");
      o.put("id", id.matches("^[\\w-]{1,40}$") ? id : UUID.randomUUID().toString().substring(0, 8));
      ObjectNode label = o.putObject("label");
      it.path("label")
          .fields()
          .forEachRemaining(e -> {
            if (LANG.matcher(e.getKey()).matches()
                && e.getValue().isTextual()
                && !e.getValue().asText().isBlank()) {
              String v = e.getValue().asText().trim();
              label.put(e.getKey(), v.length() > 40 ? v.substring(0, 40) : v);
            }
          });
      if (label.isEmpty()) {
        throw ApiException.badRequest("invalid_menu", "Every menu item needs a label.");
      }
      String pageId = it.path("pageId").asText("");
      String href = it.path("href").asText("");
      if (!pageId.isBlank()) {
        UUID pid;
        try {
          pid = UUID.fromString(pageId);
        } catch (IllegalArgumentException e) {
          throw ApiException.badRequest("invalid_menu", "A menu item points to an unknown page.");
        }
        boolean exists =
            jdbc.sql("select exists(select 1 from page where id = ?)")
                .param(pid)
                .query(Boolean.class)
                .single();
        if (!exists) {
          throw ApiException.badRequest(
              "invalid_menu", "A menu item points to a page that no longer exists.");
        }
        o.put("pageId", pid.toString());
      } else if (!href.isBlank()) {
        if (!HREF.matcher(href).matches() || href.length() > 500) {
          throw ApiException.badRequest(
              "invalid_menu", "Links must be a page path or start with https://");
        }
        o.put("href", href);
      } else if (!it.path("children").isArray() || it.path("children").isEmpty()) {
        throw ApiException.badRequest("invalid_menu", "Every menu item needs a page or a link.");
      }
      if (it.path("children").isArray() && !it.path("children").isEmpty()) {
        if (depth >= 1) {
          throw ApiException.badRequest("invalid_menu", "Menus can be nested one level deep.");
        }
        o.set("children", cleanItems(it.get("children"), depth + 1));
      }
    }
    return out;
  }

  private static void requireLocation(String location) {
    if (!LOCATIONS.contains(location)) {
      throw ApiException.notFound("Menu");
    }
  }

  // ------------------------------------------------------------------------- reusable blocks

  public List<ReusableBlock> reusableBlocks() {
    return jdbc.sql("select id, name, block::text, updated_at from reusable_block order by name")
        .query((rs, i) ->
            new ReusableBlock(
                rs.getObject(1, UUID.class),
                rs.getString(2),
                read(rs.getString(3)),
                rs.getTimestamp(4).toInstant()))
        .list();
  }

  public ReusableBlock saveReusable(UUID id, String name, JsonNode block, boolean allowRawHtml) {
    if (name == null || name.isBlank() || name.length() > 120) {
      throw ApiException.badRequest("invalid_name", "Give the block a name.");
    }
    if (block == null || "reusable".equals(block.path("type").asText())) {
      throw ApiException.badRequest("invalid_block", "This block cannot be made reusable.");
    }
    ObjectNode wrapper = json.createObjectNode().put("schemaVersion", 1);
    wrapper.putArray("blocks").add(block);
    JsonNode clean = validator.validateAndSanitise(wrapper, allowRawHtml).get("blocks").get(0);
    return tx.execute(s -> {
      UUID rid;
      if (id == null) {
        rid =
            jdbc.sql(
                    "insert into reusable_block (organization_id, name, block) values (?, ?, ?::jsonb) returning id")
                .params(TenantContext.requireOrganizationId(), name.trim(), clean.toString())
                .query(UUID.class)
                .single();
      } else {
        int n =
            jdbc.sql(
                    "update reusable_block set name = ?, block = ?::jsonb, updated_at = now() where id = ?")
                .params(name.trim(), clean.toString(), id)
                .update();
        if (n == 0) {
          throw ApiException.notFound("Reusable block");
        }
        rid = id;
        events.publishEvent(
            new PageService.PagePublished(TenantContext.requireOrganizationId(), null, null));
      }
      audit.record(
          id == null ? "reusable_block.created" : "reusable_block.updated",
          "reusable_block",
          rid.toString(),
          null,
          Map.of("name", name));
      return reusableBlocks().stream().filter(r -> r.id().equals(rid)).findFirst().orElseThrow();
    });
  }

  public void deleteReusable(UUID id) {
    tx.executeWithoutResult(s -> {
      boolean used =
          jdbc.sql(
                  "select exists(select 1 from page_content where draft_document @> ?::jsonb or published_document @> ?::jsonb)")
              .params(refJson(id), refJson(id))
              .query(Boolean.class)
              .single();
      if (used) {
        throw ApiException.conflict(
            "in_use", "This block is used on a page. Remove it from pages first.");
      }
      if (jdbc.sql("delete from reusable_block where id = ?").param(id).update() == 0) {
        throw ApiException.notFound("Reusable block");
      }
      audit.record("reusable_block.deleted", "reusable_block", id.toString(), null, null);
    });
  }

  private String refJson(UUID id) {
    return "{\"blocks\": [{\"type\": \"reusable\", \"props\": {\"refId\": \"" + id + "\"}}]}";
  }

  private JsonNode read(String raw) {
    try {
      return json.readTree(raw);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
