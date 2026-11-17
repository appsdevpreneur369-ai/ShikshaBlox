package com.shikshablox.platform.shared.web;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * RFC 9457 problem responses for every API error. Unexpected errors never leak stack traces or
 * messages; the client gets the request id to quote to support.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
  private static final String TYPE_BASE = "https://errors.shikshablox.app/";

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
    ProblemDetail problem = problem(ex.status(), ex.code(), ex.getMessage());
    var builder = ResponseEntity.status(ex.status());
    if (ex.status() == HttpStatus.TOO_MANY_REQUESTS) {
      builder.header(HttpHeaders.RETRY_AFTER, "60");
    }
    return builder.body(problem);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail handleAccessDenied(AccessDeniedException ex) {
    return problem(HttpStatus.FORBIDDEN, "forbidden", "You do not have permission to do that.");
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception ex) {
    log.error("Unhandled error", ex);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR, "internal", "Something went wrong. Please try again.");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        problem(HttpStatus.BAD_REQUEST, "validation_failed", "Some fields are not valid.");
    problem.setProperty(
        "fields",
        ex.getBindingResult().getFieldErrors().stream()
            .map(e ->
                java.util.Map.of(
                    "field",
                    e.getField(),
                    "message",
                    e.getDefaultMessage() == null ? "is invalid" : e.getDefaultMessage()))
            .toList());
    return ResponseEntity.badRequest().body(problem);
  }

  @Override
  protected ResponseEntity<Object> createResponseEntity(
      @Nullable Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    if (body instanceof ProblemDetail pd) {
      pd.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
    }
    return super.createResponseEntity(body, headers, status, request);
  }

  static ProblemDetail problem(HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(TYPE_BASE + code));
    problem.setProperty("code", code);
    problem.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
    return problem;
  }
}
