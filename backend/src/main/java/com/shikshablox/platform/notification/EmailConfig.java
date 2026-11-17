package com.shikshablox.platform.notification;

import jakarta.mail.internet.InternetAddress;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

@Configuration(proxyBeanMethods = false)
class EmailConfig {

  private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

  @Bean
  @ConditionalOnMissingBean(EmailSender.class)
  EmailSender emailSender(
      ObjectProvider<JavaMailSender> mailSender,
      @Value("${shikshablox.mail.from:ShikshaBlox <no-reply@shikshablox.localhost>}") String from) {
    JavaMailSender smtp = mailSender.getIfAvailable();
    if (smtp == null) {
      log.warn("No SMTP host configured (SPRING_MAIL_HOST); outgoing email is suppressed");
      // Never log the body: it can contain one-time codes.
      return message ->
          log.info("Email suppressed: to={} subject={}", mask(message.to()), message.subject());
    }
    return message -> {
      try {
        var mime = smtp.createMimeMessage();
        var helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
        helper.setFrom(new InternetAddress(from));
        helper.setTo(message.to());
        helper.setSubject(message.subject());
        helper.setText(message.text(), false);
        smtp.send(mime);
      } catch (Exception e) {
        log.error("Email to {} failed", mask(message.to()), e);
        throw new IllegalStateException("Email could not be sent", e);
      }
    };
  }

  static String mask(String email) {
    int at = email.indexOf('@');
    return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
  }
}
