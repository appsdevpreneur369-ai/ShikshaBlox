package com.shikshablox.platform.identity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 TOTP (HMAC-SHA1, 6 digits, 30-second steps) compatible with authenticator apps. */
final class Totp {

  static final int DIGITS = 6;
  static final long STEP_SECONDS = 30;
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

  private Totp() {}

  /** A new 160-bit secret, Base32-encoded. */
  static String newSecret() {
    byte[] bytes = new byte[20];
    RANDOM.nextBytes(bytes);
    return base32Encode(bytes);
  }

  static String otpauthUri(String issuer, String account, String secret) {
    String label = enc(issuer) + ":" + enc(account);
    return "otpauth://totp/"
        + label
        + "?secret="
        + secret
        + "&issuer="
        + enc(issuer)
        + "&algorithm=SHA1&digits=6&period=30";
  }

  /**
   * Verifies {@code code} for the current step ±1 (clock drift). Returns the matched step so the
   * caller can reject replays of an already used step.
   */
  static OptionalLong verify(String secret, String code, long epochSeconds) {
    if (code == null || !code.matches("\\d{6}")) {
      return OptionalLong.empty();
    }
    byte[] key = base32Decode(secret);
    long step = epochSeconds / STEP_SECONDS;
    for (long s = step - 1; s <= step + 1; s++) {
      if (MessageDigest.isEqual(
          generate(key, s).getBytes(StandardCharsets.US_ASCII),
          code.getBytes(StandardCharsets.US_ASCII))) {
        return OptionalLong.of(s);
      }
    }
    return OptionalLong.empty();
  }

  static String generate(byte[] key, long step) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(key, "HmacSHA1"));
      byte[] hash = mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(step).array());
      int offset = hash[hash.length - 1] & 0x0f;
      int binary =
          ((hash[offset] & 0x7f) << 24)
              | ((hash[offset + 1] & 0xff) << 16)
              | ((hash[offset + 2] & 0xff) << 8)
              | (hash[offset + 3] & 0xff);
      return String.format("%06d", binary % 1_000_000);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  static String base32Encode(byte[] data) {
    StringBuilder out = new StringBuilder();
    int buffer = 0;
    int bits = 0;
    for (byte b : data) {
      buffer = (buffer << 8) | (b & 0xff);
      bits += 8;
      while (bits >= 5) {
        out.append(BASE32[(buffer >> (bits - 5)) & 0x1f]);
        bits -= 5;
      }
    }
    if (bits > 0) {
      out.append(BASE32[(buffer << (5 - bits)) & 0x1f]);
    }
    return out.toString();
  }

  static byte[] base32Decode(String s) {
    String clean = s.replace("=", "").replace(" ", "").toUpperCase();
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    int buffer = 0;
    int bits = 0;
    for (char c : clean.toCharArray()) {
      int v = c >= 'A' && c <= 'Z' ? c - 'A' : c >= '2' && c <= '7' ? c - '2' + 26 : -1;
      if (v < 0) {
        throw new IllegalArgumentException("Invalid Base32");
      }
      buffer = (buffer << 5) | v;
      bits += 5;
      if (bits >= 8) {
        out.write((buffer >> (bits - 8)) & 0xff);
        bits -= 8;
      }
    }
    return out.toByteArray();
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
