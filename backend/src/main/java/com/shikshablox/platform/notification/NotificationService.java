package com.shikshablox.platform.notification;

import com.shikshablox.platform.notification.EmailSender.EmailMessage;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Template-based notifications (email now; SMS/WhatsApp channels plug in later). Failures are
 * logged and never break the business action that triggered them.
 */
@Service
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
  private static final Pattern VAR = Pattern.compile("\\{\\{(\\w+)}}");

  public enum Template {
    ENQUIRY_RECEIVED(
        "New admissions enquiry for {{school}}",
        """
        A new admissions enquiry has arrived on your website.

        Student: {{studentName}}
        Class sought: {{gradeSought}}
        Parent/guardian: {{guardianName}}

        Open the enquiry inbox in ShikshaBlox Studio to respond:
        {{link}}
        """),
    APPROVAL_REQUESTED(
        "Approval requested: {{title}}",
        """
        {{author}} has submitted "{{title}}" for review on the {{school}} website.

        Review it in ShikshaBlox Studio:
        {{link}}
        """),
    DISCLOSURE_REVIEW_DUE(
        "Mandatory disclosure review due for {{school}}",
        """
        The public disclosure section of your website was last reviewed on {{lastReviewed}}.
        Please check that it is still accurate and mark it as reviewed:
        {{link}}
        """);

    private final String subject;
    private final String body;

    Template(String subject, String body) {
      this.subject = subject;
      this.body = body;
    }
  }

  private final EmailSender email;

  NotificationService(EmailSender email) {
    this.email = email;
  }

  public void email(Template template, List<String> recipients, Map<String, String> vars) {
    String subject = render(template.subject, vars).replaceAll("[\\r\\n]", " ");
    String body = render(template.body, vars);
    for (String to : recipients) {
      try {
        email.send(new EmailMessage(to, subject, body));
      } catch (RuntimeException e) {
        log.warn("Notification {} could not be sent", template, e);
      }
    }
  }

  static String render(String text, Map<String, String> vars) {
    Matcher m = VAR.matcher(text);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(sb, Matcher.quoteReplacement(vars.getOrDefault(m.group(1), "")));
    }
    m.appendTail(sb);
    return sb.toString();
  }
}
