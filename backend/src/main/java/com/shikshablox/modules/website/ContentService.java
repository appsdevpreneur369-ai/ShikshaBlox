package com.shikshablox.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Website content types (M3): notices, news, events, albums, staff directory, FAQs, careers and
 * disclosures. Each type is a field specification; items are stored as validated, sanitised JSON so
 * new types need no schema change. Publishing an item refreshes the public site.
 */
@Service
public class ContentService {

  static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Kolkata");

  enum Kind {
    TEXT,
    HTML,
    DATE,
    BOOL,
    MEDIA,
    MEDIA_LIST
  }

  record Field(String name, Kind kind, boolean required, int max, String label) {}

  /** Type definition: fields, which field is the main date, whether items get a detail page. */
  record TypeSpec(
      String type, String label, List<Field> fields, String dateField, boolean slugged) {}

  private static Field f(String name, Kind kind, boolean required, int max, String label) {
    return new Field(name, kind, required, max, label);
  }

  static final Map<String, TypeSpec> TYPES =
      Map.of(
          "notices",
          new TypeSpec(
              "notices",
              "Notices",
              List.of(
                  f("title", Kind.TEXT, true, 160, "Title"),
                  f("summary", Kind.TEXT, false, 1000, "Notice text"),
                  f("category", Kind.TEXT, false, 60, "Category"),
                  f("date", Kind.DATE, true, 0, "Date"),
                  f("expiresOn", Kind.DATE, false, 0, "Show until"),
                  f("pinned", Kind.BOOL, false, 0, "Pin to top"),
                  f("attachmentId", Kind.MEDIA, false, 0, "Attachment")),
              "date",
              false),
          "news",
          new TypeSpec(
              "news",
              "News",
              List.of(
                  f("title", Kind.TEXT, true, 160, "Headline"),
                  f("summary", Kind.TEXT, false, 400, "Summary"),
                  f("date", Kind.DATE, true, 0, "Date"),
                  f("imageId", Kind.MEDIA, false, 0, "Cover image"),
                  f("bodyHtml", Kind.HTML, false, 100_000, "Story")),
              "date",
              true),
          "events",
          new TypeSpec(
              "events",
              "Events",
              List.of(
                  f("title", Kind.TEXT, true, 160, "Event name"),
                  f("summary", Kind.TEXT, false, 400, "Summary"),
                  f("date", Kind.DATE, true, 0, "Starts"),
                  f("endDate", Kind.DATE, false, 0, "Ends"),
                  f("location", Kind.TEXT, false, 160, "Location"),
                  f("imageId", Kind.MEDIA, false, 0, "Cover image"),
                  f("bodyHtml", Kind.HTML, false, 100_000, "Details")),
              "date",
              true),
          "albums",
          new TypeSpec(
              "albums",
              "Photo albums",
              List.of(
                  f("title", Kind.TEXT, true, 160, "Album name"),
                  f("summary", Kind.TEXT, false, 400, "Description"),
                  f("date", Kind.DATE, false, 0, "Date"),
                  f("imageIds", Kind.MEDIA_LIST, true, 200, "Photos")),
              "date",
              true),
          "staff",
          new TypeSpec(
              "staff",
              "Staff directory",
              List.of(
                  f("title", Kind.TEXT, true, 120, "Name"),
                  f("role", Kind.TEXT, false, 120, "Designation"),
                  f("department", Kind.TEXT, false, 80, "Department"),
                  f("qualification", Kind.TEXT, false, 160, "Qualification"),
                  f("imageId", Kind.MEDIA, false, 0, "Photo")),
              null,
              false),
          "faqs",
          new TypeSpec(
              "faqs",
              "FAQs",
              List.of(
                  f("title", Kind.TEXT, true, 300, "Question"),
                  f("summary", Kind.TEXT, true, 2000, "Answer"),
                  f("category", Kind.TEXT, false, 60, "Category")),
              null,
              false),
          "careers",
          new TypeSpec(
              "careers",
              "Careers",
              List.of(
                  f("title", Kind.TEXT, true, 160, "Position"),
                  f("department", Kind.TEXT, false, 80, "Department"),
                  f("location", Kind.TEXT, false, 120, "Location"),
                  f("date", Kind.DATE, false, 0, "Apply by"),
                  f("bodyHtml", Kind.HTML, false, 50_000, "Description")),
              "date",
              false),
          "disclosures",
          new TypeSpec(
              "disclosures",
              "Mandatory disclosures",
              List.of(
                  f("title", Kind.TEXT, true, 200, "Item"),
                  f("category", Kind.TEXT, false, 80, "Section"),
                  f("summary", Kind.TEXT, false, 2000, "Details"),
                  f("attachmentId", Kind.MEDIA, false, 0, "Document")),
              null,
              false));

  public record Item(
      UUID id,
      String type,
      String slug,
      String status,
      JsonNode data,
      int sortOrder,
      Instant itemDate,
      Instant publishedAt,
      Instant updatedAt) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final ObjectMapper json;
  private final AuditService audit;
  private final ApplicationEventPublisher events;

  ContentService(
      JdbcClient jdbc,
      TransactionTemplate tx,
      ObjectMapper json,
      AuditService audit,
      ApplicationEventPublisher events) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.json = json;
    this.audit = audit;
    this.events = events;
  }

  static TypeSpec spec(String type) {
    TypeSpec s = TYPES.get(type);
    if (s == null) {
      throw ApiException.notFound("Content type");
    }
    return s;
  }

  // --------------------------------------------------------------------------- admin

  public List<Item> list(String type) {
    spec(type);
    return jdbc.sql(
            "select * from content_item where type = ?"
                + " order by sort_order, item_date desc nulls last, created_at desc limit 500")
        .param(type)
        .query(this::map)
        .list();
  }

  public Item get(String type, UUID id) {
    return find(type, id).orElseThrow(() -> ApiException.notFound("Item"));
  }

  Optional<Item> find(String type, UUID id) {
    return jdbc.sql("select * from content_item where type = ? and id = ?")
        .params(type, id)
        .query(this::map)
        .optional();
  }

  public Item create(String type, JsonNode data, boolean publish) {
    TypeSpec s = spec(type);
    ObjectNode clean = validate(s, data);
    return tx.execute(st -> {
      String slug = s.slugged() ? uniqueSlug(type, clean.path("title").asText(), null) : null;
      int order =
          jdbc.sql("select coalesce(max(sort_order), 0) + 10 from content_item where type = ?")
              .param(type)
              .query(Integer.class)
              .single();
      UUID id =
          jdbc.sql(
                  "insert into content_item (organization_id, type, slug, status, data, item_date, sort_order,"
                      + " published_at, created_by) values (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?) returning id")
              .params(
                  TenantContext.requireOrganizationId(),
                  type,
                  slug,
                  publish ? "PUBLISHED" : "DRAFT",
                  clean.toString(),
                  itemDate(s, clean),
                  order,
                  publish ? Timestamp.from(Instant.now()) : null,
                  TenantContext.userId().orElse(null))
              .query(UUID.class)
              .single();
      audit.record(
          "content.created",
          "content:" + type,
          id.toString(),
          null,
          Map.of("title", clean.path("title").asText(), "published", publish));
      changed();
      return get(type, id);
    });
  }

  public Item update(String type, UUID id, JsonNode data) {
    TypeSpec s = spec(type);
    ObjectNode clean = validate(s, data);
    return tx.execute(st -> {
      Item before = get(type, id);
      jdbc.sql(
              "update content_item set data = ?::jsonb, item_date = ?, updated_at = now() where id = ?")
          .params(clean.toString(), itemDate(s, clean), id)
          .update();
      audit.record("content.updated", "content:" + type, id.toString(), before.data(), clean);
      if ("PUBLISHED".equals(before.status())) {
        changed();
      }
      return get(type, id);
    });
  }

  public Item setPublished(String type, UUID id, boolean published) {
    return tx.execute(st -> {
      get(type, id);
      jdbc.sql(
              "update content_item set status = ?, published_at = case when ? then coalesce(published_at, now())"
                  + " else null end, updated_at = now() where id = ?")
          .params(published ? "PUBLISHED" : "DRAFT", published, id)
          .update();
      audit.record(
          published ? "content.published" : "content.unpublished",
          "content:" + type,
          id.toString(),
          null,
          null);
      changed();
      return get(type, id);
    });
  }

  public void reorder(String type, List<UUID> ids) {
    spec(type);
    tx.executeWithoutResult(st -> {
      int i = 10;
      for (UUID id : ids) {
        jdbc.sql("update content_item set sort_order = ? where id = ? and type = ?")
            .params(i, id, type)
            .update();
        i += 10;
      }
      changed();
    });
  }

  public void delete(String type, UUID id) {
    tx.executeWithoutResult(st -> {
      Item before = get(type, id);
      jdbc.sql("delete from content_item where id = ?").param(id).update();
      audit.record("content.deleted", "content:" + type, id.toString(), before.data(), null);
      changed();
    });
  }

  // --------------------------------------------------------------------------- public feeds

  /** Published items for the public site (already in the school's tenant context). */
  List<Item> feed(String type, int limit, String show, String department) {
    spec(type);
    int n = Math.max(1, Math.min(limit, 200));
    StringBuilder sql =
        new StringBuilder("select * from content_item where type = ? and status = 'PUBLISHED'");
    List<Object> params = new ArrayList<>(List.of(type));
    Timestamp startOfToday =
        Timestamp.from(LocalDate.now(SCHOOL_ZONE).atStartOfDay(SCHOOL_ZONE).toInstant());
    String order;
    switch (type) {
      case "notices" -> {
        sql.append(
            " and (data->>'expiresOn' is null or (data->>'expiresOn')::date >= (now() at time zone 'Asia/Kolkata')::date)");
        order = " order by coalesce((data->>'pinned')::boolean, false) desc, item_date desc";
      }
      case "events" -> {
        if ("upcoming".equals(show)) {
          sql.append(
              " and coalesce(nullif(data->>'endDate', '')::date, item_date::date) >= ?::date");
          params.add(startOfToday);
          order = " order by item_date asc";
        } else if ("past".equals(show)) {
          sql.append(
              " and coalesce(nullif(data->>'endDate', '')::date, item_date::date) < ?::date");
          params.add(startOfToday);
          order = " order by item_date desc";
        } else {
          order = " order by item_date desc";
        }
      }
      case "careers" -> {
        sql.append(" and (item_date is null or item_date >= ?)");
        params.add(startOfToday);
        order = " order by item_date asc nulls last";
      }
      case "staff", "faqs", "disclosures" -> {
        if (department != null && !department.isBlank()) {
          sql.append(" and lower(data->>'department') = lower(?)");
          params.add(department);
        }
        order = " order by sort_order, created_at";
      }
      default -> order = " order by item_date desc nulls last, published_at desc";
    }
    sql.append(order).append(" limit ").append(n);
    return jdbc.sql(sql.toString()).params(params).query(this::map).list();
  }

  Optional<Item> publishedBySlug(String type, String slug) {
    return jdbc.sql(
            "select * from content_item where type = ? and slug = ? and status = 'PUBLISHED'")
        .params(type, slug)
        .query(this::map)
        .optional();
  }

  /** Media ids referenced by an item. */
  static Set<UUID> mediaIds(TypeSpec s, JsonNode data) {
    Set<UUID> ids = new LinkedHashSet<>();
    for (Field fd : s.fields()) {
      if (fd.kind() == Kind.MEDIA && data.hasNonNull(fd.name())) {
        ids.add(UUID.fromString(data.get(fd.name()).asText()));
      } else if (fd.kind() == Kind.MEDIA_LIST && data.has(fd.name())) {
        data.get(fd.name()).forEach(n -> ids.add(UUID.fromString(n.asText())));
      }
    }
    return ids;
  }

  // --------------------------------------------------------------------------- validation

  ObjectNode validate(TypeSpec s, JsonNode in) {
    if (in == null || !in.isObject()) {
      throw ApiException.badRequest("invalid_content", "Fill in the form.");
    }
    ObjectNode out = json.createObjectNode();
    for (Field fd : s.fields()) {
      JsonNode v = in.get(fd.name());
      boolean blank =
          v == null
              || v.isNull()
              || (v.isTextual() && v.asText().isBlank())
              || (v.isArray() && v.isEmpty());
      if (blank) {
        if (fd.required()) {
          throw ApiException.badRequest("missing_field", fd.label() + " is required.");
        }
        continue;
      }
      switch (fd.kind()) {
        case TEXT -> {
          String t = v.asText().trim();
          if (t.length() > fd.max()) {
            throw ApiException.badRequest(
                "too_long", fd.label() + " must be at most " + fd.max() + " characters.");
          }
          out.put(fd.name(), t);
        }
        case HTML -> {
          String h = DocumentValidator.RICH_TEXT.sanitize(v.asText());
          if (h.length() > fd.max()) {
            throw ApiException.badRequest("too_long", fd.label() + " is too long.");
          }
          out.put(fd.name(), h);
        }
        case DATE -> out.put(fd.name(), parseDate(v.asText(), fd.label()));
        case BOOL -> out.put(fd.name(), v.asBoolean());
        case MEDIA -> out.put(fd.name(), uuid(v.asText(), fd.label()));
        case MEDIA_LIST -> {
          if (!v.isArray() || v.size() > fd.max()) {
            throw ApiException.badRequest(
                "invalid_media", fd.label() + ": choose up to " + fd.max() + " files.");
          }
          ArrayNode arr = out.putArray(fd.name());
          Set<String> seen = new LinkedHashSet<>();
          v.forEach(n -> seen.add(uuid(n.asText(), fd.label())));
          seen.forEach(arr::add);
        }
      }
    }
    if (out.has("endDate")
        && out.has("date")
        && out.get("endDate").asText().compareTo(out.get("date").asText()) < 0) {
      throw ApiException.badRequest("invalid_dates", "The end date must be after the start date.");
    }
    return out;
  }

  /** Accepts a date (2026-11-18) or a local date-time (2026-11-18T10:00); keeps the given form. */
  static String parseDate(String raw, String label) {
    String v = raw.trim();
    try {
      if (v.length() <= 10) {
        return LocalDate.parse(v).toString();
      }
      if (v.endsWith("Z") || v.matches(".*[+-]\\d\\d:\\d\\d$")) {
        return OffsetDateTime.parse(v).atZoneSameInstant(SCHOOL_ZONE).toLocalDateTime().toString();
      }
      return LocalDateTime.parse(v).toString();
    } catch (DateTimeParseException e) {
      throw ApiException.badRequest("invalid_date", label + " is not a valid date.");
    }
  }

  static Instant toInstant(String localDateOrDateTime) {
    if (localDateOrDateTime.length() <= 10) {
      return LocalDate.parse(localDateOrDateTime).atStartOfDay(SCHOOL_ZONE).toInstant();
    }
    return LocalDateTime.parse(localDateOrDateTime).atZone(SCHOOL_ZONE).toInstant();
  }

  private static String uuid(String raw, String label) {
    try {
      return UUID.fromString(raw.trim()).toString();
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest(
          "invalid_media", label + ": choose a file from the media library.");
    }
  }

  private static Timestamp itemDate(TypeSpec s, ObjectNode clean) {
    if (s.dateField() == null || !clean.has(s.dateField())) {
      return null;
    }
    return Timestamp.from(toInstant(clean.get(s.dateField()).asText()));
  }

  String uniqueSlug(String type, String title, UUID exceptId) {
    String base =
        Normalizer.normalize(title, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
    if (base.isEmpty()) {
      base = "item";
    }
    if (base.length() > 70) {
      base = base.substring(0, 70).replaceAll("-+$", "");
    }
    String slug = base;
    for (int i = 2; ; i++) {
      boolean taken =
          jdbc.sql(
                  "select exists(select 1 from content_item where type = ? and slug = ? and id is distinct from ?)")
              .params(type, slug, exceptId)
              .query(Boolean.class)
              .single();
      if (!taken) {
        return slug;
      }
      slug = base + "-" + i;
    }
  }

  private void changed() {
    events.publishEvent(
        new PageService.PagePublished(TenantContext.requireOrganizationId(), null, null));
  }

  private Item map(ResultSet rs, int i) throws SQLException {
    try {
      return new Item(
          rs.getObject("id", UUID.class),
          rs.getString("type"),
          rs.getString("slug"),
          rs.getString("status"),
          json.readTree(rs.getString("data")),
          rs.getInt("sort_order"),
          ts(rs, "item_date"),
          ts(rs, "published_at"),
          ts(rs, "updated_at"));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Instant ts(ResultSet rs, String col) throws SQLException {
    Timestamp t = rs.getTimestamp(col);
    return t == null ? null : t.toInstant();
  }
}
