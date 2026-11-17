package com.shikshablox.platform.shared.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lightweight public liveness endpoint used by the frontends and smoke tests. */
@RestController
@RequestMapping("/api/public/v1/system")
@Tag(name = "System")
public class SystemController {

  public record PingResponse(String status, String service, Instant time) {}

  @GetMapping("/ping")
  @Operation(operationId = "ping", summary = "Liveness check for clients")
  public PingResponse ping() {
    return new PingResponse("ok", "shikshablox-backend", Instant.now());
  }
}
