package com.shikshablox.modules.comms;

/**
 * Sends a WhatsApp or SMS message through the school's provider account. Implementations for Meta
 * Cloud API / Gupshup plug in here once a provider account is connected.
 */
interface MessageProvider {

  /** Outcome of one send: the provider's message id, or an error. */
  record Result(boolean ok, String reference, String error, boolean testMode) {}

  /**
   * @param channel WHATSAPP or SMS
   * @param to phone number
   * @param templateName approved template (WhatsApp) or DLT template id (SMS)
   * @param text rendered message text
   */
  Result send(String channel, String to, String templateName, String text);
}
