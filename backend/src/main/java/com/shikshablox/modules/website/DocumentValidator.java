package com.shikshablox.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.shikshablox.platform.shared.web.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * Validates page documents against the JSON Schema generated from {@code packages/blocks} and
 * sanitises every HTML field with an allow-list. Raw-HTML blocks are restricted to school
 * administrators and still sanitised (no scripts, styles or event handlers; iframes only from
 * YouTube, Vimeo and Google Maps).
 */
@Component
class DocumentValidator {

  private static final Pattern EMBED_SRC =
      Pattern.compile(
          "^https://(www\\.youtube-nocookie\\.com/embed/|www\\.youtube\\.com/embed/|player\\.vimeo\\.com/video/|www\\.google\\.com/maps/embed).*");

  static final PolicyFactory RICH_TEXT =
      new HtmlPolicyBuilder()
          .allowElements(
              "p",
              "h2",
              "h3",
              "h4",
              "strong",
              "b",
              "em",
              "i",
              "u",
              "s",
              "a",
              "ul",
              "ol",
              "li",
              "blockquote",
              "br",
              "hr",
              "table",
              "thead",
              "tbody",
              "tr",
              "th",
              "td",
              "caption")
          .allowUrlProtocols("https", "http", "mailto", "tel")
          .allowAttributes("href")
          .onElements("a")
          .requireRelsOnLinks("noopener", "noreferrer")
          .allowAttributes("scope", "colspan", "rowspan")
          .onElements("th", "td")
          .toFactory();

  static final PolicyFactory CUSTOM_HTML =
      RICH_TEXT.and(
          new HtmlPolicyBuilder()
              .allowElements("div", "span", "section", "figure", "figcaption", "img", "iframe")
              .allowAttributes("class")
              .matching(Pattern.compile("^[\\w\\s:-]{0,200}$"))
              .globally()
              .allowUrlProtocols("https")
              .allowAttributes("src", "alt", "width", "height", "loading")
              .onElements("img")
              .allowAttributes("src")
              .matching(EMBED_SRC)
              .onElements("iframe")
              .allowAttributes("title", "width", "height", "allowfullscreen", "loading")
              .onElements("iframe")
              .toFactory());

  private final JsonSchema schema;

  DocumentValidator() {
    try (InputStream in = getClass().getResourceAsStream("/schemas/page-document.schema.json")) {
      SchemaValidatorsConfig config =
          SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
      this.schema =
          JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(in, config);
    } catch (IOException e) {
      throw new IllegalStateException("Page document schema missing", e);
    }
  }

  /**
   * Validates and returns a sanitised copy. {@code allowRawHtml} is true only for school
   * administrators.
   */
  JsonNode validateAndSanitise(JsonNode document, boolean allowRawHtml) {
    if (document == null || !document.isObject()) {
      throw ApiException.badRequest("invalid_document", "The page content is not valid.");
    }
    Set<ValidationMessage> errors = schema.validate(document);
    if (!errors.isEmpty()) {
      throw ApiException.badRequest(
          "invalid_document", "The page content is not valid: " + summarise(errors));
    }
    ObjectNode copy = document.deepCopy();
    Set<String> ids = new LinkedHashSet<>();
    for (JsonNode block : copy.withArray("blocks")) {
      if (!ids.add(block.get("id").asText())) {
        throw ApiException.badRequest("invalid_document", "Two blocks share the same id.");
      }
      String type = block.get("type").asText();
      ObjectNode props = (ObjectNode) block.get("props");
      if (type.equals("customHtml")) {
        if (!allowRawHtml) {
          throw new ApiException(
              org.springframework.http.HttpStatus.FORBIDDEN,
              "raw_html_admin_only",
              "Only school administrators can add custom HTML.");
        }
        props.put("html", CUSTOM_HTML.sanitize(props.get("html").asText()));
      } else if (props.has("html")) {
        props.put("html", RICH_TEXT.sanitize(props.get("html").asText()));
      }
    }
    return copy;
  }

  /** Every media id referenced anywhere in a document (or block). */
  static Set<UUID> mediaIds(JsonNode node) {
    Set<UUID> out = new LinkedHashSet<>();
    collect(node, out);
    return out;
  }

  private static void collect(JsonNode node, Set<UUID> out) {
    if (node == null) {
      return;
    }
    if (node.isObject()) {
      node.fields()
          .forEachRemaining(e -> {
            String k = e.getKey();
            JsonNode v = e.getValue();
            if ((k.equals("imageId") || k.equals("mediaId")) && v.isTextual()) {
              parse(v.asText(), out);
            } else if (k.equals("imageIds") && v.isArray()) {
              v.forEach(x -> parse(x.asText(), out));
            } else {
              collect(v, out);
            }
          });
    } else if (node.isArray()) {
      ((ArrayNode) node).forEach(x -> collect(x, out));
    }
  }

  /** Ids of reusable blocks referenced by a document. */
  static Set<UUID> reusableRefs(JsonNode document) {
    Set<UUID> out = new LinkedHashSet<>();
    for (JsonNode b : document.path("blocks")) {
      if ("reusable".equals(b.path("type").asText())) {
        parse(b.path("props").path("refId").asText(), out);
      }
    }
    return out;
  }

  private static void parse(String s, Set<UUID> out) {
    try {
      out.add(UUID.fromString(s));
    } catch (IllegalArgumentException ignored) {
      // schema validation already rejects malformed ids
    }
  }

  private static String summarise(Set<ValidationMessage> errors) {
    List<String> msgs = errors.stream().limit(3).map(ValidationMessage::getMessage).toList();
    String s = String.join("; ", msgs);
    return s.length() > 400 ? s.substring(0, 400) + "…" : s;
  }
}
