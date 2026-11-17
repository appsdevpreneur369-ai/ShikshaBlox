package com.shikshablox.platform.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * Published for every authentication, MFA and permission event so the audit module can record it
 * without identity depending on audit.
 */
public record SecurityEvent(
    String type, UUID userId, UUID organizationId, String ip, boolean success, Instant at) {

  public static final String LOGIN = "auth.login";
  public static final String LOGIN_FAILED = "auth.login_failed";
  public static final String ACCOUNT_LOCKED = "auth.account_locked";
  public static final String OTP_SENT = "auth.otp_sent";
  public static final String MFA_ENROLLED = "auth.mfa_enrolled";
  public static final String MFA_FAILED = "auth.mfa_failed";
  public static final String REFRESH_REUSE = "auth.refresh_reuse_detected";
  public static final String LOGOUT = "auth.logout";
  public static final String SWITCH_ORG = "auth.switch_organization";
  public static final String ROLE_GRANTED = "identity.role_granted";
}
