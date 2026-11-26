package com.shikshablox.modules.comms;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Used until a school connects a WhatsApp / SMS provider: records the message as sent without
 * contacting anyone, so broadcasts, receipts and reports can be tried end to end. Deliveries are
 * flagged as test mode in Studio and cost nothing.
 */
@Component
class TestModeProvider implements MessageProvider {

  private static final Logger log = LoggerFactory.getLogger(TestModeProvider.class);

  @Override
  public Result send(String channel, String to, String templateName, String text) {
    String masked = to == null || to.length() < 4 ? "****" : "****" + to.substring(to.length() - 4);
    log.info(
        "[test mode] {} to {} via template {} ({} chars)",
        channel,
        masked,
        templateName,
        text.length());
    return new Result(true, "test-" + UUID.randomUUID(), null, true);
  }
}
