package com.shikshablox.modules.engagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.shikshablox.platform.shared.web.ApiException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.regex.Pattern;

/** Validation helpers shared by the PTM, helpdesk and survey services. */
final class Inputs {

  static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  private static final Pattern PHONE = Pattern.compile("^[+0-9 \\-]{8,20}$");
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  /** No 0/O/1/I/L, so codes read out over the phone are unambiguous. */
  private static final char[] CODE_CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();

  private static final SecureRandom RANDOM = new SecureRandom();

  private Inputs() {}

  static LocalDate today() {
    return LocalDate.now(IST);
  }

  static String required(String v, int max, String label) {
    String t = v == null ? "" : v.trim();
    if (t.isEmpty()) {
      throw ApiException.badRequest("missing_field", label + " is required.");
    }
    if (t.length() > max) {
      throw ApiException.badRequest("too_long", label + " is too long.");
    }
    return t;
  }

  static String optional(String v, int max, String label) {
    return v == null || v.isBlank() ? null : required(v, max, label);
  }

  static String text(JsonNode in, String key, int max, boolean req, String label) {
    JsonNode n = in == null ? null : in.get(key);
    String v = n == null || n.isNull() ? null : n.asText();
    return req ? required(v, max, label) : optional(v, max, label);
  }

  static String phone(String v) {
    String t = required(v, 20, "Phone");
    if (!PHONE.matcher(t).matches() || t.replaceAll("\\D", "").length() < 8) {
      throw ApiException.badRequest("invalid_phone", "Enter a valid phone number.");
    }
    return t;
  }

  /** Last 10 digits, for matching a phone number however it was typed. */
  static String phoneKey(String phone) {
    String d = phone == null ? "" : phone.replaceAll("\\D", "");
    return d.length() > 10 ? d.substring(d.length() - 10) : d;
  }

  static String email(String v) {
    String t = optional(v, 200, "Email");
    if (t != null && !EMAIL.matcher(t).matches()) {
      throw ApiException.badRequest("invalid_email", "Enter a valid email address.");
    }
    return t;
  }

  static String oneOf(String v, Set<String> allowed, String field) {
    if (v == null || !allowed.contains(v)) {
      throw ApiException.badRequest("invalid_" + field, "Unknown " + field + ".");
    }
    return v;
  }

  static String code(int length) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)]);
    }
    return sb.toString();
  }

  /** CSV cell, neutralising spreadsheet formulas. */
  static String cell(String v) {
    if (v == null) {
      return "";
    }
    String s = v;
    if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
      s = "'" + s;
    }
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }
}
