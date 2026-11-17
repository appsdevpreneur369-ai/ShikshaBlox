package com.shikshablox.platform.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Registered configuration keys with type, default and who may change them. Values resolve PLATFORM
 * -> PROFILE -> ORGANIZATION -> CAMPUS; the most specific value wins.
 */
public enum ConfigKey {
  SECURITY_REQUIRE_MFA_SCHOOL_ADMIN(
      "security.require_mfa_school_admin",
      Type.BOOLEAN,
      JsonNodeFactory.instance.booleanNode(false),
      true,
      "Require school administrators to use an authenticator app"),
  WEBSITE_DEFAULT_LANGUAGE(
      "website.default_language",
      Type.STRING,
      JsonNodeFactory.instance.textNode("en"),
      true,
      "Default website language (ISO code)"),
  DISCLOSURE_REVIEW_DAYS(
      "disclosure.review_interval_days",
      Type.INTEGER,
      JsonNodeFactory.instance.numberNode(180),
      true,
      "Days after which disclosure content should be reviewed again"),
  ENQUIRY_NOTIFICATION_EMAILS(
      "enquiry.notification_emails",
      Type.STRING_LIST,
      JsonNodeFactory.instance.arrayNode(),
      true,
      "Who receives an email for each new admissions enquiry"),
  MEDIA_MAX_IMAGE_MB(
      "media.max_image_mb",
      Type.INTEGER,
      JsonNodeFactory.instance.numberNode(10),
      false,
      "Largest image upload (MB)"),
  MEDIA_MAX_DOCUMENT_MB(
      "media.max_document_mb",
      Type.INTEGER,
      JsonNodeFactory.instance.numberNode(25),
      false,
      "Largest document upload (MB)"),
  MEDIA_MAX_VIDEO_MB(
      "media.max_video_mb",
      Type.INTEGER,
      JsonNodeFactory.instance.numberNode(50),
      false,
      "Largest MP4 upload (MB)");

  public enum Type {
    BOOLEAN,
    INTEGER,
    STRING,
    STRING_LIST
  }

  private final String key;
  private final Type type;
  private final JsonNode defaultValue;
  private final boolean schoolEditable;
  private final String description;

  ConfigKey(
      String key, Type type, JsonNode defaultValue, boolean schoolEditable, String description) {
    this.key = key;
    this.type = type;
    this.defaultValue = defaultValue;
    this.schoolEditable = schoolEditable;
    this.description = description;
  }

  public String key() {
    return key;
  }

  public Type type() {
    return type;
  }

  public JsonNode defaultValue() {
    return defaultValue;
  }

  public boolean schoolEditable() {
    return schoolEditable;
  }

  public String description() {
    return description;
  }

  public static Optional<ConfigKey> of(String key) {
    return Arrays.stream(values()).filter(k -> k.key.equals(key)).findFirst();
  }

  /** True if {@code value} has this key's type. */
  public boolean accepts(JsonNode value) {
    if (value == null) {
      return false;
    }
    return switch (type) {
      case BOOLEAN -> value.isBoolean();
      case INTEGER -> value.isIntegralNumber() && value.asLong() >= 0 && value.asLong() <= 100_000;
      case STRING -> value.isTextual() && value.asText().length() <= 500;
      case STRING_LIST -> {
        if (!value.isArray() || value.size() > 50) {
          yield false;
        }
        List<JsonNode> items = new java.util.ArrayList<>();
        value.forEach(items::add);
        yield items.stream().allMatch(n -> n.isTextual() && n.asText().length() <= 254);
      }
    };
  }
}
