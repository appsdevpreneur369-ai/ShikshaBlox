package com.shikshablox.platform.shared.web;

import org.springframework.http.HttpStatus;

/**
 * An expected, client-facing failure. The message is shown to users, so it must never contain
 * internal details. {@code code} is a stable machine-readable identifier.
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  public ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public static ApiException notFound(String what) {
    return new ApiException(HttpStatus.NOT_FOUND, "not_found", what + " not found.");
  }

  public static ApiException forbidden() {
    return new ApiException(
        HttpStatus.FORBIDDEN, "forbidden", "You do not have permission to do that.");
  }

  public static ApiException badRequest(String code, String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, message);
  }

  public static ApiException conflict(String code, String message) {
    return new ApiException(HttpStatus.CONFLICT, code, message);
  }

  public static ApiException tooManyRequests() {
    return new ApiException(
        HttpStatus.TOO_MANY_REQUESTS,
        "rate_limited",
        "Too many attempts. Please wait a few minutes and try again.");
  }
}
