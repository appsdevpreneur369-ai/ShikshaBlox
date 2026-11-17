package com.shikshablox.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.files.InMemoryObjectStorage;
import com.shikshablox.platform.identity.Role;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.TenantContext;
import com.shikshablox.support.ApiTestSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.LinkedMultiValueMap;

/** Entitlements, configuration hierarchy, audit log, consent and media through the API. */
class PlatformServicesIT extends ApiTestSupport {

  @Autowired EntitlementService entitlements;
  @Autowired InMemoryObjectStorage storage;
  @Autowired JdbcClient jdbc;

  // ------------------------------------------------------------------------- entitlements

  @Test
  void newSchoolsStartOnWebFreeAndPlanChangesAreAudited() {
    Organization org = school("ent");
    String admin = tokenFor(org, Role.SCHOOL_ADMIN);

    var free = get("/api/admin/v1/entitlements", admin).getBody();
    assertThat(free.get("plan").asText()).isEqualTo("WEB_FREE");
    assertThat(limit(free, "custom_pages").get("limit").asLong()).isEqualTo(5);
    assertThat(limit(free, "languages").get("limit").asLong()).isEqualTo(1);
    assertThat(limit(free, "custom_domains").get("state").asText()).isEqualTo("BLOCKED");
    assertThat(free.toString()).doesNotContain("approval_workflow");

    entitlements.applyPlan(org.id(), "WEB_PRO");
    var pro = get("/api/admin/v1/entitlements", admin).getBody();
    assertThat(pro.get("plan").asText()).isEqualTo("WEB_PRO");
    assertThat(limit(pro, "custom_pages").get("limit").asLong()).isEqualTo(-1);
    assertThat(pro.toString()).contains("approval_workflow", "custom_domain");

    var audit = get("/api/admin/v1/audit?action=entitlement.", admin).getBody();
    assertThat(audit.toString()).contains("entitlement.plan_changed", "WEB_PRO");
  }

  @Test
  void schoolsCannotGrantThemselvesEntitlements() {
    Organization org = school("ent2");
    try (var ignored = TenantContext.forTenant(org.id(), null)) {
      assertThat(
              jdbc.sql("update entitlement set limits = '{\"custom_pages\": 999}'::jsonb").update())
          .isZero();
      assertThatThrownBy(
              () -> entitlements.checkCanAdd("WEBSITE", "custom_domains", 1, "custom domains"))
          .hasMessageContaining("Upgrade to Web Pro");
      entitlements.checkCanAdd("WEBSITE", "custom_pages", 5, "pages"); // within limit
      assertThatThrownBy(() -> entitlements.checkCanAdd("WEBSITE", "custom_pages", 6, "pages"))
          .hasMessageContaining("limit");
    }
  }

  @Test
  void limitStatesWarnAt80AndBlockAt100Percent() {
    assertThat(stateOf(10, 7)).isEqualTo("OK");
    assertThat(stateOf(10, 8)).isEqualTo("WARNING");
    assertThat(stateOf(10, 10)).isEqualTo("BLOCKED");
    assertThat(stateOf(-1, 1_000_000)).isEqualTo("OK");
    assertThat(stateOf(0, 0)).isEqualTo("BLOCKED");
  }

  private static String stateOf(long limit, long used) {
    return EntitlementService.limitState(limit, used).name();
  }

  @Test
  void platformEndpointsNeedPlatformAdmin() {
    Organization org = school("ent3");
    String admin = tokenFor(org, Role.SCHOOL_ADMIN);
    assertThat(
            send(
                    HttpMethod.PUT,
                    "/api/platform/v1/organizations/" + org.id() + "/plan",
                    Map.of("planCode", "WEB_PRO"),
                    admin)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  // ------------------------------------------------------------------------- configuration

  @Test
  void configurationResolvesMostSpecificLevel() {
    Organization org = school("cfg");
    String admin = tokenFor(org, Role.SCHOOL_ADMIN);
    String editor = tokenFor(org, Role.CONTENT_EDITOR);

    assertThat(setting(admin, "disclosure.review_interval_days").get("source").asText())
        .isEqualTo("DEFAULT");

    assertThat(
            send(
                    HttpMethod.PUT,
                    "/api/admin/v1/settings/disclosure.review_interval_days",
                    Map.of("value", 90),
                    admin)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    JsonNode s = setting(admin, "disclosure.review_interval_days");
    assertThat(s.get("value").asInt()).isEqualTo(90);
    assertThat(s.get("source").asText()).isEqualTo("ORGANIZATION");

    // Wrong type, platform-only key, and non-admin are all refused.
    assertThat(
            send(
                    HttpMethod.PUT,
                    "/api/admin/v1/settings/disclosure.review_interval_days",
                    Map.of("value", "soon"),
                    admin)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            send(
                    HttpMethod.PUT,
                    "/api/admin/v1/settings/media.max_image_mb",
                    Map.of("value", 500),
                    admin)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            send(
                    HttpMethod.PUT,
                    "/api/admin/v1/settings/disclosure.review_interval_days",
                    Map.of("value", 30),
                    editor)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    var audit = get("/api/admin/v1/audit?action=config.", admin).getBody();
    assertThat(audit.toString()).contains("disclosure.review_interval_days");
  }

  @Test
  void schoolCanRequireAuthenticatorForItsAdmins() {
    Organization org = school("mfa");
    String adminEmail = user("admin", org, Role.SCHOOL_ADMIN);
    String admin = token(adminEmail);
    send(
        HttpMethod.PUT,
        "/api/admin/v1/settings/security.require_mfa_school_admin",
        Map.of("value", true),
        admin);

    var login =
        send(
            HttpMethod.POST,
            "/api/auth/v1/login",
            Map.of("email", adminEmail, "password", PASSWORD),
            null);
    assertThat(login.getBody().get("status").asText()).isEqualTo("MFA_ENROLLMENT_REQUIRED");
  }

  private JsonNode setting(String token, String key) {
    for (JsonNode n : get("/api/admin/v1/settings", token).getBody()) {
      if (n.get("key").asText().equals(key)) {
        return n;
      }
    }
    throw new AssertionError("missing " + key);
  }

  // ------------------------------------------------------------------------- audit

  @Test
  void auditLogIsAppendOnlyAndTenantScoped() {
    Organization a = school("auda");
    Organization b = school("audb");
    String adminA = tokenFor(a, Role.SCHOOL_ADMIN);
    String adminB = tokenFor(b, Role.SCHOOL_ADMIN);
    String viewerA = tokenFor(a, Role.VIEWER);

    assertThat(get("/api/admin/v1/audit", adminA).getBody().toString())
        .contains("organization.created", "auth.login")
        .doesNotContain(b.id().toString());
    assertThat(get("/api/admin/v1/audit", adminB).getBody().toString())
        .doesNotContain(a.id().toString());
    assertThat(get("/api/admin/v1/audit", viewerA).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    TenantContext.runAsSystem(
        "test",
        () -> {
          assertThatThrownBy(() -> jdbc.sql("update audit_log set action = 'x'").update())
              .isNotNull();
          assertThatThrownBy(() -> jdbc.sql("delete from audit_log").update()).isNotNull();
        });
  }

  // ------------------------------------------------------------------------- consent + media

  @Test
  void identifiableMediaNeedsConsentBeforeItIsPublishable() throws Exception {
    Organization org = school("media");
    String admin = tokenFor(org, Role.SCHOOL_ADMIN);

    var uploaded = upload(admin, png(1200, 800), "annual-day.png", true);
    assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode m = uploaded.getBody();
    assertThat(m.get("kind").asText()).isEqualTo("IMAGE");
    assertThat(m.get("width").asInt()).isEqualTo(1200);
    assertThat(m.at("/variants/thumb/width").asInt()).isEqualTo(320);
    assertThat(m.at("/variants/medium/width").asInt()).isEqualTo(960);
    assertThat(m.at("/variants/large/width").asInt()).isEqualTo(1200); // never upscaled
    assertThat(m.get("needsAltText").asBoolean()).isTrue();
    assertThat(m.get("publishable").asBoolean()).isFalse();
    String id = m.get("id").asText();

    // Original stays private (it may carry GPS metadata); variants are public.
    assertThat(storage.objects.keySet())
        .anyMatch(k -> k.startsWith("PRIVATE:orgs/" + org.id()) && k.contains("original"));
    assertThat(storage.objects.keySet())
        .anyMatch(k -> k.startsWith("PUBLIC:orgs/" + org.id()) && k.contains("thumb"));

    var consent =
        send(
            HttpMethod.POST,
            "/api/admin/v1/consents",
            Map.of(
                "kind",
                "GUARDIAN_CONSENT",
                "reference",
                "Annual Day 2026 consent forms on file",
                "declaredBy",
                "Principal"),
            admin);
    assertThat(consent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String consentId = consent.getBody().get("id").asText();

    var linked =
        send(
            HttpMethod.PATCH,
            "/api/admin/v1/media/" + id,
            Map.of("consentRecordId", consentId, "altText", "Students on stage"),
            admin);
    assertThat(linked.getBody().get("publishable").asBoolean()).isTrue();
    assertThat(linked.getBody().get("needsAltText").asBoolean()).isFalse();

    send(HttpMethod.DELETE, "/api/admin/v1/consents/" + consentId, null, admin);
    assertThat(get("/api/admin/v1/media/" + id, admin).getBody().get("publishable").asBoolean())
        .isFalse();

    // A school-wide declaration also allows publishing.
    send(
        HttpMethod.POST,
        "/api/admin/v1/consents",
        Map.of(
            "kind",
            "SCHOOL_DECLARATION",
            "reference",
            "Admission form photo consent 2026-27",
            "declaredBy",
            "Principal"),
        admin);
    assertThat(get("/api/admin/v1/media/" + id, admin).getBody().get("publishable").asBoolean())
        .isTrue();

    // Storage usage is metered.
    var usage = get("/api/admin/v1/entitlements", admin).getBody();
    assertThat(limit(usage, "storage_bytes").get("used").asLong()).isPositive();
  }

  @Test
  void uploadsAreValidatedBySignatureAndIsolatedPerSchool() throws Exception {
    Organization a = school("upa");
    Organization b = school("upb");
    String editorA = tokenFor(a, Role.CONTENT_EDITOR);
    String adminB = tokenFor(b, Role.SCHOOL_ADMIN);
    String viewerA = tokenFor(a, Role.VIEWER);

    byte[] svg =
        "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes();
    assertThat(upload(editorA, svg, "logo.svg", false).getBody().get("code").asText())
        .isEqualTo("unsupported_type");
    byte[] fakePng = "MZ this is an exe".getBytes();
    assertThat(upload(editorA, fakePng, "photo.png", false).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    byte[] pdf = "%PDF-1.4\n%test document\n".getBytes();
    var doc = upload(editorA, pdf, "fee-structure.pdf", false);
    assertThat(doc.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(doc.getBody().get("kind").asText()).isEqualTo("DOCUMENT");
    String docId = doc.getBody().get("id").asText();

    assertThat(upload(viewerA, pdf, "x.pdf", false).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(get("/api/admin/v1/media/" + docId, adminB).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            send(HttpMethod.DELETE, "/api/admin/v1/media/" + docId, null, adminB).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get("/api/admin/v1/media", adminB).getBody().toString()).doesNotContain(docId);

    assertThat(
            send(HttpMethod.DELETE, "/api/admin/v1/media/" + docId, null, editorA).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(storage.objects.keySet()).noneMatch(k -> k.contains(docId));
  }

  // ------------------------------------------------------------------------- helpers

  private ResponseEntity<JsonNode> upload(
      String token, byte[] content, String filename, boolean identifiable) {
    var form = new LinkedMultiValueMap<String, Object>();
    form.add(
        "file",
        new ByteArrayResource(content) {
          @Override
          public String getFilename() {
            return filename;
          }
        });
    form.add("containsIdentifiableStudents", String.valueOf(identifiable));
    return send(HttpMethod.POST, "/api/admin/v1/media", form, token);
  }

  private static byte[] png(int w, int h) throws Exception {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    var g = img.createGraphics();
    g.setColor(java.awt.Color.ORANGE);
    g.fillRect(0, 0, w, h);
    g.dispose();
    var out = new ByteArrayOutputStream();
    ImageIO.write(img, "png", out);
    return out.toByteArray();
  }

  private static JsonNode limit(JsonNode entitlements, String key) {
    for (JsonNode n : entitlements.get("limits")) {
      if (n.get("key").asText().equals(key)) {
        return n;
      }
    }
    throw new AssertionError("no limit " + key + " in " + entitlements);
  }
}
