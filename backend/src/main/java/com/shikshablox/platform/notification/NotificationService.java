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
        """),
    FOLLOW_UPS_DUE(
        "{{count}} admission follow-ups due today at {{school}}",
        """
        Good morning! You have {{count}} admission follow-ups due today ({{overdue}} overdue).

        Open your follow-up list in ShikshaBlox Studio:
        {{link}}
        """),
    APPLICATION_RECEIVED(
        "New admission application {{applicationNo}} for {{school}}",
        """
        A new admission application has been submitted on your website.

        Application: {{applicationNo}}
        Class: {{grade}}
        Documents uploaded: {{documents}}

        Review the application and documents in ShikshaBlox Studio:
        {{link}}
        """),
    HELPDESK_STAFF(
        "{{what}}: {{ticketNo}} {{subject}}",
        """
        {{what}} on the parent helpdesk.

        Request: {{ticketNo}}
        Subject: {{subject}}

        Open the helpdesk in ShikshaBlox Studio:
        {{link}}
        """),
    HELPDESK_REPLY(
        "Update on your request {{ticketNo}} ({{school}})",
        """
        {{school}} has replied to your request {{ticketNo}} ({{subject}}):

        {{reply}}

        You can follow your request on the school website (Help page) with this number and
        your phone number.
        """),
    COMPLIANCE_DUE(
        "Compliance reminders for {{school}}",
        """
        Good morning! These compliance items need attention:

        {{items}}

        Open the compliance calendar in ShikshaBlox Studio:
        {{link}}
        """),
    APPLICATION_ACKNOWLEDGED(
        "Application {{applicationNo}} received by {{school}}",
        """
        Thank you for applying to {{school}}.

        Your application number is {{applicationNo}} ({{grade}}).
        Please quote it when you contact the school.
        {{fee}}
        The admissions office will review your documents and get in touch with you.
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

  /**
   * A one-off plain-text email (e.g. a school circular). Returns false when it could not be handed
   * to the mail server.
   */
  public boolean plain(String to, String subject, String body) {
    try {
      email.send(new EmailMessage(to, subject.replaceAll("[\r\n]", " "), body));
      return true;
    } catch (RuntimeException e) {
      log.warn("Email to a recipient could not be sent: {}", e.getMessage());
      return false;
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
