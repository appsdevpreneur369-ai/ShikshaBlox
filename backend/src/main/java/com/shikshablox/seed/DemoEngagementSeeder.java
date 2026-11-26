package com.shikshablox.seed;

import com.shikshablox.modules.engagement.HelpdeskService;
import com.shikshablox.modules.engagement.PtmService;
import com.shikshablox.modules.engagement.SurveyService;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.OrganizationService;
import com.shikshablox.platform.tenancy.TenantContext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
 * Sample PTM day, helpdesk requests and an open feedback survey for the fictional Greenfield Demo
 * School. Runs once (skipped once the survey exists). Phone numbers use the 90000 4xxxx sample
 * range.
 */
@Component
@Order(34)
@ConditionalOnProperty(name = "shikshablox.seed.enabled", havingValue = "true")
class DemoEngagementSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoEngagementSeeder.class);

  private final OrganizationService organizations;
  private final PtmService ptm;
  private final HelpdeskService helpdesk;
  private final SurveyService surveys;
  private final JdbcClient jdbc;

  DemoEngagementSeeder(
      OrganizationService organizations,
      PtmService ptm,
      HelpdeskService helpdesk,
      SurveyService surveys,
      JdbcClient jdbc) {
    this.organizations = organizations;
    this.ptm = ptm;
    this.helpdesk = helpdesk;
    this.surveys = surveys;
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
            // The survey is seeded last, so it marks a complete run; a partial earlier run is
            // cleared (sample rows only) and redone.
            if (jdbc.sql("select count(*) from survey").query(Long.class).single() > 0) {
              return null;
            }
            jdbc.sql("delete from ptm_event where instructions like '%(sample)%'").update();
            jdbc.sql(
                    "delete from helpdesk_ticket where id in"
                        + " (select ticket_id from helpdesk_message where body like '%(sample)%')")
                .update();
            LocalDate day = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(6);
            PtmService.EventSummary e =
                ptm.create(
                    new PtmService.EventInput(
                        "Term 2 parent-teacher meeting",
                        day,
                        LocalTime.of(9, 0),
                        LocalTime.of(12, 0),
                        10,
                        "Please come 5 minutes early and bring the report card. (sample)",
                        true,
                        List.of(
                            new PtmService.HostInput(
                                "Mrs Lakshmi Rao", "Class 3A · Class teacher", "Room 12"),
                            new PtmService.HostInput(
                                "Mr Vivek Sharma", "Class 5B · Maths", "Room 21"),
                            new PtmService.HostInput(
                                "Ms Ayesha Khan", "Class 7A · Science", "Lab 2"))));
            PtmService.Schedule s = ptm.schedule(e.id());
            book(
                org,
                s.hosts().get(0).id(),
                LocalTime.of(9, 0),
                "Priya Sharma",
                "+91 90000 40001",
                "Aarav Sharma",
                "3A",
                "DEMO01");
            book(
                org,
                s.hosts().get(0).id(),
                LocalTime.of(9, 20),
                "Kiran Kumar",
                "+91 90000 40002",
                "Anaya Kumar",
                "3A",
                "DEMO02");
            book(
                org,
                s.hosts().get(1).id(),
                LocalTime.of(10, 0),
                "Meera Iyer",
                "+91 90000 40003",
                "Ishaan Iyer",
                "5B",
                "DEMO03");

            HelpdeskService.TicketDetail t1 =
                helpdesk.create(
                    new HelpdeskService.TicketInput(
                        "TRANSPORT",
                        "Bus 4 arriving late in the morning",
                        "The school bus on route 4 has been 20 minutes late all this week. (sample)",
                        "Rahul Verma",
                        "+91 90000 40011",
                        "rahul.verma@example.test",
                        "Diya Verma",
                        "UKG",
                        "HIGH",
                        "PHONE"));
            helpdesk.reply(
                t1.ticket().id(),
                "We have spoken to the transport contractor; the timing will be fixed from Monday.",
                true,
                "IN_PROGRESS");
            helpdesk.create(
                new HelpdeskService.TicketInput(
                    "FEES",
                    "Receipt for term 2 fee",
                    "I paid the term 2 fee online but did not get a receipt. (sample)",
                    "Sneha Reddy",
                    "+91 90000 40012",
                    null,
                    "Vihaan Reddy",
                    "Class 5",
                    "NORMAL",
                    "WALK_IN"));
            HelpdeskService.TicketDetail t3 =
                helpdesk.create(
                    new HelpdeskService.TicketInput(
                        "CERTIFICATES",
                        "Bonafide certificate for passport",
                        "Please issue a bonafide certificate for my son's passport application. (sample)",
                        "Arjun Nair",
                        "+91 90000 40013",
                        null,
                        "Saanvi Nair",
                        "Class 2",
                        "LOW",
                        "PHONE"));
            helpdesk.reply(
                t3.ticket().id(),
                "The certificate is ready at the front office.",
                true,
                "RESOLVED");

            SurveyService.Survey sv =
                surveys.create(
                    new SurveyService.SurveyInput(
                        "Annual Day feedback",
                        "annual-day-feedback",
                        "Tell us how we did. It takes one minute and is anonymous. (sample)",
                        List.of(
                            new SurveyService.Question(
                                null, "RATING", "Overall, how was the Annual Day?", null, true),
                            new SurveyService.Question(
                                null,
                                "CHOICE",
                                "Was the timing convenient?",
                                List.of("Yes", "No", "Partly"),
                                false),
                            new SurveyService.Question(
                                null,
                                "MULTI",
                                "What did you enjoy?",
                                List.of("Dance", "Drama", "Music", "Prize distribution"),
                                false),
                            new SurveyService.Question(
                                null, "TEXT", "Any suggestions?", null, false)),
                        true,
                        true,
                        null));
            String[] answers = {
              "{\"q1\":5,\"q2\":\"Yes\",\"q3\":[\"Dance\",\"Music\"],\"q4\":\"Lovely event, thank you!\"}",
              "{\"q1\":4,\"q2\":\"Partly\",\"q3\":[\"Drama\"],\"q4\":\"Please start a little earlier.\"}",
              "{\"q1\":5,\"q2\":\"Yes\",\"q3\":[\"Dance\",\"Prize distribution\"]}",
              "{\"q1\":3,\"q2\":\"No\",\"q4\":\"Parking was difficult.\"}"
            };
            for (String a : answers) {
              jdbc.sql(
                      "insert into survey_response (organization_id, survey_id, answers) values (?, ?, ?::jsonb)")
                  .params(org.id(), sv.id(), a)
                  .update();
            }
            log.info("Seeded a sample PTM, helpdesk requests and a survey for greenfield");
            return null;
          });
    } catch (RuntimeException e) {
      log.warn("Demo PTM/helpdesk/survey seed skipped: {}", e.getMessage());
    }
  }

  private void book(
      Organization org,
      java.util.UUID hostId,
      LocalTime slot,
      String parent,
      String phone,
      String student,
      String cls,
      String code) {
    jdbc.sql(
            "insert into ptm_booking (organization_id, host_id, slot_start, parent_name, phone, student_name,"
                + " student_class, code) values (?, ?, ?, ?, ?, ?, ?, ?)")
        .params(org.id(), hostId, slot, parent, phone, student, cls, code)
        .update();
  }
}
