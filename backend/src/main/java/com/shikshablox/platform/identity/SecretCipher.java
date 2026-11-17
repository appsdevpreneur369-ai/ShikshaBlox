package com.shikshablox.platform.identity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM encryption for secrets at rest (TOTP seeds). The key is derived from the configured
 * data key; output is {@code v1:<base64(iv || ciphertext)>}.
 */
@Component
class SecretCipher {

  private static final SecureRandom RANDOM = new SecureRandom();
  private final SecretKeySpec key;

  SecretCipher(IdentityProperties props) {
    try {
      byte[] k =
          MessageDigest.getInstance("SHA-256")
              .digest(props.dataKey().getBytes(StandardCharsets.UTF_8));
      this.key = new SecretKeySpec(k, "AES");
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  String encrypt(String plain) {
    try {
      byte[] iv = new byte[12];
      RANDOM.nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
      byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      return "v1:"
          + Base64.getEncoder()
              .encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  String decrypt(String stored) {
    if (stored == null || !stored.startsWith("v1:")) {
      throw new IllegalArgumentException("Unsupported secret format");
    }
    try {
      byte[] all = Base64.getDecoder().decode(stored.substring(3));
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
      return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Secret could not be decrypted", e);
    }
  }
}
