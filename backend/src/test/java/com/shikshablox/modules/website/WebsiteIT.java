package com.shikshablox.modules.website;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shikshablox.platform.identity.Role;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.support.ApiTestSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;

class WebsiteIT extends ApiTestSupport {

  final ObjectMapper json = new ObjectMapper();

  Map<String, Object> onboarding(String primary) {
    return Map.of(
        "theme", "modern",
        "brand",
            Map.of(
                "primary",
                primary,
                "accent",
                "#f59e0b",
                "fontHeading",
                "Poppins",
                "fontBody",
                "Inter",
                "radius",
                "round"),
        "profile",
            Map.of(
                "tagline",
                "Learning with joy",
                "board",
                "CBSE",
                "address",
                Map.of("city", "Guntur"),
                "email",
                "office@school.test"));
  }

  String onboarded(Organization org) {
    String admin = tokenFor(org, Role.SCHOOL_ADMIN);
    var res = send(HttpMethod.POST, "/api/admin/v1/site/onboarding", onboarding("#1e3a8a"), admin);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    return admin;
  }

  JsonNode pageBySlug(String token, String slug) {
    for (JsonNode p : get("/api/admin/v1/pages", token).getBody()) {
      if (p.get("slug").asText().equals(slug)) {
        return p;
      }
    }
    throw new AssertionError("no page " + slug);
  }

  @Test
  void onboardingGeneratesAPublishedSiteWithFourteenPages() {
    Organization org = school("web");
    String admin = onboarded(org);

    JsonNode pages = get("/api/admin/v1/pages", admin).getBody();
    assertThat(pages).hasSize(14);
    assertThat(pages.findValuesAsText("status")).containsOnly("PUBLISHED");

    String host = org.slug() + ".shikshablox.localhost";
    JsonNode site = get("/api/public/v1/site?host=" + host, null).getBody();
    assertThat(site.get("name").asText()).isEqualTo(org.name());
    assertThat(site.get("showBadge").asBoolean()).isTrue(); // Web Free keeps the badge
    assertThat(site.get("header").toString()).contains("/admissions", "Principal's Message");

    JsonNode home = get("/api/public/v1/site/page?host=" + host + "&path=", null).getBody();
    assertThat(home.at("/document/blocks/0/type").asText()).isEqualTo("hero");
    assertThat(home.at("/document/blocks/0/props/heading").asText()).contains(org.name());
    assertThat(
            get("/api/public/v1/site/page?host=" + host + "&path=admissions", null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(get("/api/public/v1/site/page?host=" + host + "&path=nope", null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get("/api/public/v1/site/paths?host=" + host, null).getBody()).hasSize(14);
  }

  @Test
  void draftsAreSanitisedAndOnlyPublishedContentIsPublic() throws Exception {
    Organization org = school("draft");
    String admin = onboarded(org);
    String editor = tokenFor(org, Role.CONTENT_EDITOR);
    String host = org.slug() + ".shikshablox.localhost";
    String aboutId = pageBySlug(admin, "about").get("id").asText();

    Map<String, Object> doc =
        Map.of(
            "schemaVersion",
            1,
            "blocks",
            List.of(
                Map.of(
                    "id",
                    "t1",
                    "type",
                    "richText",
                    "props",
                    Map.of(
                        "html",
                        "<p onclick=\"steal()\">Hello <script>alert(1)</script><a href=\"javascript:x\">x</a></p>"))));
    var saved =
        send(
            HttpMethod.PUT,
            "/api/admin/v1/pages/" + aboutId,
            Map.of("title", "About", "document", doc),
            editor);
    assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
    String html = saved.getBody().at("/draftDocument/blocks/0/props/html").asText();
    assertThat(html).contains("Hello").doesNotContain("script", "onclick", "javascript");

    // Draft is not live yet; editors cannot publish.
    assertThat(
            get("/api/public/v1/site/page?host=" + host + "&path=about", null).getBody().toString())
        .doesNotContain("Hello");
    assertThat(
            send(HttpMethod.POST, "/api/admin/v1/pages/" + aboutId + "/publish", Map.of(), editor)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            send(
                    HttpMethod.POST,
                    "/api/admin/v1/pages/" + aboutId + "/publish",
                    Map.of("note", "copy edit"),
                    admin)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            get("/api/public/v1/site/page?host=" + host + "&path=about", null).getBody().toString())
        .contains("Hello");
    assertThat(get("/api/admin/v1/pages/" + aboutId + "/revisions", admin).getBody())
        .hasSizeGreaterThanOrEqualTo(2);

    // Raw HTML blocks: editors are refused, admins get a sanitised result.
    Map<String, Object> raw =
        Map.of(
            "schemaVersion",
            1,
            "blocks",
            List.of(
                Map.of(
                    "id",
                    "c1",
                    "type",
                    "customHtml",
                    "props",
                    Map.of(
                        "html",
                        "<div class=\"x\">Hi<iframe src=\"https://evil.example/\"></iframe><script>x()</script></div>"))));
    assertThat(
            send(
                    HttpMethod.PUT,
                    "/api/admin/v1/pages/" + aboutId,
                    Map.of("title", "About", "document", raw),
                    editor)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    var adminRaw =
        send(
            HttpMethod.PUT,
            "/api/admin/v1/pages/" + aboutId,
            Map.of("title", "About", "document", raw),
            admin);
    assertThat(adminRaw.getBody().at("/draftDocument/blocks/0/props/html").asText())
        .contains("Hi")
        .doesNotContain("evil.example", "script", "src=");

    // Schema violations are rejected.
    Map<String, Object> bad =
        Map.of(
            "schemaVersion",
            1,
            "blocks",
            List.of(
                Map.of(
                    "id",
                    "h",
                    "type",
                    "hero",
                    "props",
                    Map.of(
                        "variant",
                        "split",
                        "heading",
                        "x",
                        "primaryCta",
                        Map.of("label", "Go", "href", "javascript:alert(1)")))));
    assertThat(
            send(
                    HttpMethod.PUT,
                    "/api/admin/v1/pages/" + aboutId,
                    Map.of("title", "About", "document", bad),
                    admin)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void customPagesAreLimitedOnWebFreeButStandardPagesAreNot() {
    Organization org = school("limit");
    String admin = onboarded(org);
    for (int i = 1; i <= 5; i++) {
      assertThat(
              send(
                      HttpMethod.POST,
                      "/api/admin/v1/pages",
                      Map.of("slug", "extra-" + i, "title", "Extra " + i),
                      admin)
                  .getStatusCode())
          .isEqualTo(HttpStatus.CREATED);
    }
    var sixth =
        send(
            HttpMethod.POST,
            "/api/admin/v1/pages",
            Map.of("slug", "extra-6", "title", "Extra 6"),
            admin);
    assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(sixth.getBody().get("code").asText()).isEqualTo("limit_reached");
    assertThat(
            send(
                    HttpMethod.POST,
                    "/api/admin/v1/pages",
                    Map.of("slug", "api", "title", "Bad"),
                    admin)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    // Standard pages cannot be deleted; languages are limited to one on Web Free.
    String homeId = pageBySlug(admin, "").get("id").asText();
    assertThat(
            send(HttpMethod.DELETE, "/api/admin/v1/pages/" + homeId, null, admin).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            send(
                    HttpMethod.PUT,
                    "/api/admin/v1/site/languages",
                    Map.of("languages", List.of("en", "hi")),
                    admin)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void pagesAreIsolatedBetweenSchools() {
    Organization a = school("isoa");
    Organization b = school("isob");
    String adminA = onboarded(a);
    String adminB = onboarded(b);
    String pageA = pageBySlug(adminA, "about").get("id").asText();

    assertThat(get("/api/admin/v1/pages/" + pageA, adminB).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    Map<String, Object> doc = Map.of("schemaVersion", 1, "blocks", List.of());
    assertThat(
            send(
                    HttpMethod.PUT,
                    "/api/admin/v1/pages/" + pageA,
                    Map.of("title", "Hacked", "document", doc),
                    adminB)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            send(HttpMethod.POST, "/api/admin/v1/pages/" + pageA + "/publish", Map.of(), adminB)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    // A menu cannot point at another school's page.
    var menu = List.of(Map.of("id", "x", "label", Map.of("en", "Theirs"), "pageId", pageA));
    assertThat(send(HttpMethod.PUT, "/api/admin/v1/menus/header", menu, adminB).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void designNeedsAccessibleContrast() {
    Organization org = school("contrast");
    String admin = tokenFor(org, Role.SCHOOL_ADMIN);
    var res = send(HttpMethod.POST, "/api/admin/v1/site/onboarding", onboarding("#ffd54f"), admin);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody().get("code").asText()).isEqualTo("low_contrast");
  }

  @Test
  void publicPagesOmitIdentifiablePhotosWithoutConsent() throws Exception {
    Organization org = school("consent");
    String admin = onboarded(org);
    String host = org.slug() + ".shikshablox.localhost";

    String plain = upload(admin, false);
    String kids = upload(admin, true);
    String homeId = pageBySlug(admin, "").get("id").asText();
    Map<String, Object> doc =
        Map.of(
            "schemaVersion",
            1,
            "blocks",
            List.of(
                Map.of(
                    "id",
                    "g",
                    "type",
                    "galleryStrip",
                    "props",
                    Map.of("layout", "grid", "imageIds", List.of(plain, kids)))));
    send(
        HttpMethod.PUT,
        "/api/admin/v1/pages/" + homeId,
        Map.of("title", "Home", "document", doc),
        admin);
    send(HttpMethod.POST, "/api/admin/v1/pages/" + homeId + "/publish", Map.of(), admin);

    JsonNode media =
        get("/api/public/v1/site/page?host=" + host + "&path=", null).getBody().get("media");
    assertThat(media.has(plain)).isTrue();
    assertThat(media.has(kids)).isFalse();
    assertThat(media.get(plain).get("srcset")).isNotEmpty();
  }

  private String upload(String token, boolean identifiable) throws Exception {
    BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
    var out = new ByteArrayOutputStream();
    ImageIO.write(img, "png", out);
    var form = new LinkedMultiValueMap<String, Object>();
    form.add(
        "file",
        new ByteArrayResource(out.toByteArray()) {
          @Override
          public String getFilename() {
            return "p.png";
          }
        });
    form.add("containsIdentifiableStudents", String.valueOf(identifiable));
    form.add("altText", "Campus");
    return send(HttpMethod.POST, "/api/admin/v1/media", form, token).getBody().get("id").asText();
  }
}
