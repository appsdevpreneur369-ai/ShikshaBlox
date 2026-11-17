package com.shikshablox.platform.notification;

/**
 * Sends transactional email. SMTP in local/dev (Mailpit) and production; when no SMTP host is
 * configured, messages are suppressed and only their metadata is logged.
 */
public interface EmailSender {

  /** A plain-text email (HTML templates arrive with the notification centre). */
  record EmailMessage(String to, String subject, String text) {}

  void send(EmailMessage message);
}
