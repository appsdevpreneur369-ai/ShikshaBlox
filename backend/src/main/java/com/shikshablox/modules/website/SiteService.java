package com.shikshablox.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.files.MediaService;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.TenantContext;
import java.sql.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** A school's site: theme, brand tokens, profile and languages. */
@Service
public class SiteService {

  public static final Set<String> THEMES = Set.of("classic", "modern", "playful");
  public static final Set<String> FONTS =
      Set.of("Inter", "Poppins", "Merriweather", "Playfair Display", "Nunito", "Lora", "Baloo 2");
  public static final Set<String> RADII = Set.of("soft", "round", "pill");

  /** Languages the platform supports today (any ISO code can be added later; RTL-ready model). */
  public static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "hi", "te");

  private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");

  public record Site(
      UUID organizationId,
      String theme,
      JsonNode brand,
      JsonNode profile,
      UUID logoMediaId,
      List<String> languages,
      String defaultLanguage,
      boolean onboardingCompleted) {}

  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final ObjectMapper json;
  private final AuditService audit;
  private final MediaService media;
  private final EntitlementService entitlements;

  SiteService(
      JdbcClient jdbc,
      TransactionTemplate tx,
      ObjectMapper json,
      AuditService audit,
      MediaService media,
      EntitlementService entitlements) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.json = json;
    this.audit = audit;
    this.media = media;
    this.entitlements = entitlements;
  }

  public static ObjectNode defaultBrand(ObjectMapper json) {
    return json.createObjectNode()
        .put("primary", "#1e3a8a")
        .put("accent", "#f59e0b")
        .put("fontHeading", "Poppins")
        .put("fontBody", "Inter")
        .put("radius", "round");
  }

  /** The current school's site, created with defaults on first access. */
  public Site current() {
    UUID org = TenantContext.requireOrganizationId();
    return find(org)
        .orElseGet(() -> {
          jdbc.sql(
                  "insert into site (organization_id, brand) values (?, ?::jsonb) on conflict do nothing")
              .params(org, defaultBrand(json).toString())
              .update();
          return find(org).orElseThrow();
        });
  }

  public Optional<Site> find(UUID org) {
    return jdbc.sql(
            "select organization_id, theme, brand::text, profile::text, logo_media_id, languages,"
                + " default_language, onboarding_completed from site where organization_id = ?")
        .param(org)
        .query((rs, i) -> {
          Array langs = rs.getArray("languages");
          return new Site(
              rs.getObject("organization_id", UUID.class),
              rs.getString("theme"),
              read(rs.getString("brand")),
              read(rs.getString("profile")),
              rs.getObject("logo_media_id", UUID.class),
              Arrays.asList((String[]) langs.getArray()),
              rs.getString("default_language"),
              rs.getBoolean("onboarding_completed"));
        })
        .optional();
  }

  /** Validates and stores theme, brand and logo. */
  public Site updateDesign(String theme, JsonNode brand, UUID logoMediaId) {
    if (!THEMES.contains(theme)) {
      throw ApiException.badRequest("invalid_theme", "Choose Classic, Modern or Playful.");
    }
    ObjectNode cleanBrand = validateBrand(brand);
    if (logoMediaId != null) {
      var logo = media.require(logoMediaId);
      if (logo.kind() != MediaService.Kind.IMAGE) {
        throw ApiException.badRequest("invalid_logo", "The logo must be an image.");
      }
    }
    return tx.execute(s -> {
      Site before = current();
      jdbc.sql(
              "update site set theme = ?, brand = ?::jsonb, logo_media_id = ?, updated_at = now()"
                  + " where organization_id = ?")
          .params(theme, cleanBrand.toString(), logoMediaId, before.organizationId())
          .update();
      audit.record(
          "site.design_updated",
          "site",
          before.organizationId().toString(),
          Map.of("theme", before.theme(), "brand", before.brand()),
          Map.of("theme", theme, "brand", cleanBrand));
      return current();
    });
  }

  public Site updateProfile(JsonNode profile) {
    ObjectNode clean = validateProfile(profile);
    return tx.execute(s -> {
      Site before = current();
      jdbc.sql("update site set profile = ?::jsonb, updated_at = now() where organization_id = ?")
          .params(clean.toString(), before.organizationId())
          .update();
      audit.record(
          "site.profile_updated",
          "site",
          before.organizationId().toString(),
          before.profile(),
          clean);
      return current();
    });
  }

  /** Sets the site languages (first = default). Limited by the plan. */
  public Site updateLanguages(List<String> languages) {
    if (languages == null || languages.isEmpty() || !SUPPORTED_LANGUAGES.containsAll(languages)) {
      throw ApiException.badRequest("invalid_languages", "Choose from the supported languages.");
    }
    List<String> distinct = languages.stream().distinct().toList();
    long limit = entitlements.limit("WEBSITE", "languages");
    if (limit != EntitlementService.UNLIMITED && distinct.size() > limit) {
      throw ApiException.badRequest(
          "limit_reached",
          "Your plan allows "
              + limit
              + " language"
              + (limit == 1 ? "" : "s")
              + ". Upgrade to Web Pro for more.");
    }
    return tx.execute(s -> {
      Site before = current();
      jdbc.sql(
              "update site set languages = ?, default_language = ?, updated_at = now() where organization_id = ?")
          .params(distinct.toArray(String[]::new), distinct.get(0), before.organizationId())
          .update();
      audit.record(
          "site.languages_updated",
          "site",
          before.organizationId().toString(),
          before.languages(),
          distinct);
      return current();
    });
  }

  void markOnboarded() {
    jdbc.sql(
            "update site set onboarding_completed = true, updated_at = now() where organization_id = ?")
        .param(TenantContext.requireOrganizationId())
        .update();
  }

  // ------------------------------------------------------------------------- validation

  ObjectNode validateBrand(JsonNode brand) {
    ObjectNode b = defaultBrand(json);
    if (brand != null && brand.isObject()) {
      brand
          .fields()
          .forEachRemaining(e -> {
            if (Set.of("primary", "accent", "fontHeading", "fontBody", "radius")
                    .contains(e.getKey())
                && e.getValue().isTextual()) {
              b.put(e.getKey(), e.getValue().asText());
            }
          });
    }
    for (String k : List.of("primary", "accent")) {
      if (!HEX.matcher(b.get(k).asText()).matches()) {
        throw ApiException.badRequest("invalid_colour", "Colours must look like #1e3a8a.");
      }
    }
    if (!FONTS.contains(b.get("fontHeading").asText())
        || !FONTS.contains(b.get("fontBody").asText())) {
      throw ApiException.badRequest("invalid_font", "Choose a font from the list.");
    }
    if (!RADII.contains(b.get("radius").asText())) {
      throw ApiException.badRequest("invalid_radius", "Choose a corner style from the list.");
    }
    // WCAG 2.1 AA: white text on the primary colour (buttons, header) needs 4.5:1.
    double c = Contrast.ratio(b.get("primary").asText(), "#ffffff");
    if (c < 4.5) {
      throw ApiException.badRequest(
          "low_contrast",
          String.format(
              "White text on this primary colour has contrast %.1f:1; at least 4.5:1 is needed. Choose a darker colour.",
              c));
    }
    return b;
  }

  private static final Set<String> PROFILE_TEXT_FIELDS =
      Set.of(
          "tagline",
          "board",
          "boardName",
          "established",
          "phone",
          "email",
          "whatsapp",
          "mapQuery",
          "admissionsText",
          "affiliationNo",
          "schoolCode",
          "officeHours");

  ObjectNode validateProfile(JsonNode profile) {
    ObjectNode out = json.createObjectNode();
    if (profile == null || !profile.isObject()) {
      return out;
    }
    for (String f : PROFILE_TEXT_FIELDS) {
      JsonNode v = profile.get(f);
      if (v != null && v.isTextual() && !v.asText().isBlank()) {
        out.put(f, clip(v.asText(), f.equals("admissionsText") ? 400 : 200));
      }
    }
    if (out.has("email") && !out.get("email").asText().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
      throw ApiException.badRequest("invalid_email", "Enter a valid email address.");
    }
    if (profile.path("admissionsOpen").isBoolean()) {
      out.put("admissionsOpen", profile.get("admissionsOpen").asBoolean());
    }
    JsonNode addr = profile.get("address");
    if (addr != null && addr.isObject()) {
      ObjectNode a = out.putObject("address");
      for (String f : List.of("line1", "line2", "city", "state", "pincode", "country")) {
        if (addr.path(f).isTextual() && !addr.get(f).asText().isBlank()) {
          a.put(f, clip(addr.get(f).asText(), 120));
        }
      }
    }
    JsonNode social = profile.get("social");
    if (social != null && social.isObject()) {
      ObjectNode so = out.putObject("social");
      for (String f : List.of("facebook", "instagram", "youtube", "x", "linkedin")) {
        String v = social.path(f).asText("");
        if (!v.isBlank()) {
          if (!v.startsWith("https://")) {
            throw ApiException.badRequest("invalid_link", "Social links must start with https://");
          }
          so.put(f, clip(v, 300));
        }
      }
    }
    return out;
  }

  private static String clip(String s, int max) {
    String t = s.trim();
    return t.length() > max ? t.substring(0, max) : t;
  }

  private JsonNode read(String raw) {
    try {
      return json.readTree(raw);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** WCAG relative-luminance contrast. */
  static final class Contrast {
    static double ratio(String a, String b) {
      double l1 = lum(a);
      double l2 = lum(b);
      return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
    }

    private static double lum(String hex) {
      int rgb = Integer.parseInt(hex.substring(1), 16);
      double r = ch((rgb >> 16) & 0xff);
      double g = ch((rgb >> 8) & 0xff);
      double bl = ch(rgb & 0xff);
      return 0.2126 * r + 0.7152 * g + 0.0722 * bl;
    }

    private static double ch(int v) {
      double c = v / 255.0;
      return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
  }
}
