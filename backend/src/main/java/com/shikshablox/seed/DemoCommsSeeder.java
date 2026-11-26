package com.shikshablox.seed;

import com.shikshablox.modules.comms.BroadcastService;
import com.shikshablox.modules.comms.CommsService;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Sample communication hub for the fictional Greenfield Demo School: guardians with opt-ins (some
 * Hindi-speaking), English + Hindi templates, and one circular already sent in test mode with some
 * reads and acknowledgements. Nothing is really sent (test-mode provider, .test addresses). Runs
 * once; phone numbers use the 90000 5xxxx sample range.
 */
@Component
@Order(35)
@ConditionalOnProperty(name = "shikshablox.seed.enabled", havingValue = "true")
class DemoCommsSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoCommsSeeder.class);

  private record Guardian(
      String name,
      String students,
      String tag,
      String lang,
      boolean wa,
      boolean sms,
      boolean email) {}

  private static final List<Guardian> GUARDIANS =
      List.of(
          new Guardian("Priya Sharma", "Aarav Sharma (1A)", "Class 1A", "en", true, false, true),
          new Guardian("Rahul Verma", "Diya Verma (1A)", "Class 1A", "hi", true, true, false),
          new Guardian("Sneha Reddy", "Vihaan Reddy (3A)", "Class 3A", "en", true, false, false),
          new Guardian("Kiran Kumar", "Anaya Kumar (3A)", "Class 3A", "en", false, false, true),
          new Guardian("Meera Iyer", "Ishaan Iyer (3A)", "Class 3A", "en", true, false, true),
          new Guardian("Arjun Nair", "Saanvi Nair (5B)", "Class 5B", "en", true, false, false),
          new Guardian("Lakshmi Rao", "Reyansh Rao (5B)", "Class 5B", "hi", true, false, false),
          new Guardian("Suresh Babu", "Myra Babu (5B)", "Class 5B", "en", false, true, false),
          new Guardian("Fatima Khan", "Kabir Khan (1A)", "Class 1A", "hi", true, false, true),
          new Guardian("Vikram Singh", "Aadhya Singh (3A)", "Class 3A", "en", false, false, false),
          new Guardian("Anjali Mehta", "Riya Mehta (5B)", "Class 5B", "en", true, false, true),
          new Guardian("Deepak Joshi", "Arjun Joshi (1A)", "Class 1A", "en", true, false, false));

  private final OrganizationService organizations;
  private final CommsService comms;
  private final BroadcastService broadcasts;
  private final JdbcClient jdbc;

  DemoCommsSeeder(
      OrganizationService organizations,
      CommsService comms,
      BroadcastService broadcasts,
      JdbcClient jdbc) {
    this.organizations = organizations;
    this.comms = comms;
    this.broadcasts = broadcasts;
    this.jdbc = jdbc;
  }

  @Override
  public void run(ApplicationArguments args) {
    Organization org = organizations.findBySlug("greenfield").orElse(null);
    if (org == null) {
      return;
    }
    try {
      TenantContext.callInTenant(
          org.id(),
          () -> {
            // A sent circular marks a complete run; anything from a partial run is cleared first.
            if (jdbc.sql("select count(*) from comms_broadcast where status = 'SENT'")
                    .query(Long.class)
                    .single()
                > 0) {
              return null;
            }
            jdbc.sql("delete from comms_broadcast").update();
            jdbc.sql("delete from comms_contact where phone like '+91 90000 5%'").update();
            jdbc.sql("delete from comms_template").update();
            int i = 0;
            for (Guardian g : GUARDIANS) {
              boolean bus = i % 3 == 0;
              comms.create(
                  new CommsService.ContactInput(
                      g.name(),
                      String.format("+91 90000 5%04d", ++i),
                      g.email() ? g.name().toLowerCase().replace(' ', '.') + "@example.test" : null,
                      g.lang(),
                      bus ? List.of(g.tag(), "Bus 4") : List.of(g.tag()),
                      g.students(),
                      g.wa(),
                      g.sms(),
                      g.email(),
                      "Admission form consent (sample data)"),
                  "FORM");
            }
            comms.saveTemplate(
                null,
                new CommsService.TemplateInput(
                    "school_circular",
                    "en",
                    "UTILITY",
                    "{{school}}: {{title}}",
                    "Dear {{name}}, {{school}} has shared a new circular: {{title}}. Please read it here: {{link}}",
                    "APPROVED",
                    null));
            comms.saveTemplate(
                null,
                new CommsService.TemplateInput(
                    "school_circular",
                    "hi",
                    "UTILITY",
                    "{{school}}: {{title}}",
                    "प्रिय {{name}}, {{school}} की ओर से नया परिपत्र: {{title}}। कृपया यहाँ पढ़ें: {{link}}",
                    "APPROVED",
                    null));
            comms.saveTemplate(
                null,
                new CommsService.TemplateInput(
                    "event_invite",
                    "en",
                    "UTILITY",
                    "Invitation: {{title}}",
                    "Dear {{name}}, you are invited: {{title}}. Details and RSVP: {{link}}",
                    "DRAFT",
                    null));

            BroadcastService.Broadcast b =
                broadcasts.save(
                    null,
                    new BroadcastService.BroadcastInput(
                        "Annual Day on Saturday: timings and dress code",
                        "Annual Day will be held on Saturday from 5:00 pm in the school auditorium.\n\n"
                            + "Students should report by 4:15 pm in their house colours. Parents are welcome from 4:45 pm; "
                            + "please carry the invitation card.\n\n(Sample circular for the demo school.)",
                        "school_circular",
                        List.of("WHATSAPP", "SMS", "EMAIL"),
                        List.of(),
                        true,
                        "Will your child take part in the dance performance?",
                        false,
                        null));
            broadcasts.send(b.id());
            broadcasts.runNow(b.id());
            // Some parents have opened and acknowledged it.
            jdbc.sql(
                    "update comms_delivery set read_at = sent_at + interval '20 minutes' where broadcast_id = ?"
                        + " and status = 'SENT' and contact_name in ('Priya Sharma', 'Rahul Verma', 'Sneha Reddy', 'Meera Iyer', 'Arjun Nair', 'Fatima Khan')")
                .param(b.id())
                .update();
            jdbc.sql(
                    "update comms_delivery set ack_at = read_at + interval '2 minutes', consent_answer = contact_name <> 'Arjun Nair'"
                        + " where broadcast_id = ? and contact_name in ('Priya Sharma', 'Rahul Verma', 'Meera Iyer', 'Arjun Nair')")
                .param(b.id())
                .update();
            log.info("Seeded a sample communication hub for greenfield");
            return null;
          });
    } catch (RuntimeException e) {
      log.warn("Demo communication hub seed skipped: {}", e.getMessage());
    }
  }
}
