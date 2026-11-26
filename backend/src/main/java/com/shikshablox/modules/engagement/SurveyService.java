package com.shikshablox.modules.engagement;

import static com.shikshablox.modules.engagement.Inputs.optional;
import static com.shikshablox.modules.engagement.Inputs.required;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shikshablox.platform.audit.AuditService;
import com.shikshablox.platform.entitlement.EntitlementService;
import com.shikshablox.platform.shared.RateLimiter;
import com.shikshablox.platform.shared.web.ApiException;
import com.shikshablox.platform.tenancy.Organization;
import com.shikshablox.platform.tenancy.TenantContext;
import com.shikshablox.platform.tenancy.TenantResolver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Feedback surveys: the school builds a short form (rating, single or multiple choice, text),
 * shares its website link, and sees results per question. Anonymous by default.
 */
@Service
public class SurveyService {

  static final String MODULE = "ENGAGEMENT";
  static final Set<String> TYPES = Set.of("RATING", "CHOICE", "MULTI", "TEXT");
  private static final Pattern SLUG = Pattern.compile("^[a-z0-9-]{3,60}$");

  public record Question(
      String id, String type, String label, List<String> options, boolean required) {}

  public record SurveyInput(
      String title,
      String slug,
      String description,
      List<Question> questions,
      Boolean open,
      Boolean anonymous,
      LocalDate closesOn) {}

  public record Survey(
      UUID id,
      String slug,
      String title,
      String description,
      List<Question> questions,
      boolean open,
      boolean anonymous,
      LocalDate closesOn,
      long responses,
      Instant createdAt) {}

  /** Per-question results: average for ratings, counts for choices, latest answers for text. */
  public record QuestionResult(
      String id,
      String type,
      String label,
      long answered,
      Double average,
      Map<String, Long> counts,
      List<String> texts) {}

  public record Results(Survey survey, List<QuestionResult> questions) {}

  public record PublicSurvey(
      String title, String description, List<Question> questions, boolean anonymous) {}

  private final TenantResolver resolver;
  private final JdbcClient jdbc;
  private final TransactionTemplate tx;
  private final RateLimiter limiter;
  private final AuditService audit;
  private final EntitlementService entitlements;
  private final ObjectMapper json;

  SurveyService(
      TenantResolver resolver,
      JdbcClient jdbc,
      TransactionTemplate tx,
      RateLimiter limiter,
      AuditService audit,
      EntitlementService entitlements,
      ObjectMapper json) {
    this.resolver = resolver;
    this.jdbc = jdbc;
    this.tx = tx;
    this.limiter = limiter;
    this.audit = audit;
    this.entitlements = entitlements;
    this.json = json;
  }

  // ------------------------------------------------------------------ staff

  public List<Survey> list() {
    entitlements.requireFeature(MODULE, "surveys");
    return jdbc.sql(SURVEY_SELECT + " order by s.created_at desc limit 200")
        .query(this::map)
        .list();
  }

  public Survey create(SurveyInput in) {
    entitlements.requireFeature(MODULE, "surveys");
    String title = required(in.title(), 160, "Title");
    String slug = in.slug() == null || in.slug().isBlank() ? slugOf(title) : in.slug();
    if (!SLUG.matcher(slug).matches()) {
      throw ApiException.badRequest(
          "invalid_slug", "The link may use lowercase letters, digits and dashes (3–60).");
    }
    List<Question> qs = questions(in.questions());
    try {
      return tx.execute(s -> {
        UUID id =
            jdbc.sql(
                    "insert into survey (organization_id, slug, title, description, questions, is_open, anonymous,"
                        + " closes_on, created_by) values (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?) returning id")
                .params(
                    TenantContext.requireOrganizationId(),
                    slug,
                    title,
                    optional(in.description(), 2000, "Description"),
                    write(qs),
                    Boolean.TRUE.equals(in.open()),
                    in.anonymous() == null || in.anonymous(),
                    in.closesOn(),
                    TenantContext.userId().orElse(null))
                .query(UUID.class)
                .single();
        audit.record("survey.created", "survey", id.toString(), null, Map.of("title", title));
        return survey(id);
      });
    } catch (DuplicateKeyException e) {
      throw ApiException.conflict("slug_taken", "Another survey already uses that link.");
    }
  }

  /** Edits a survey. Questions can't be changed once responses exist (results would mix). */
  public Survey update(UUID id, SurveyInput in) {
    entitlements.requireFeature(MODULE, "surveys");
    return tx.execute(s -> {
      Survey before = survey(id);
      List<Question> qs = before.questions();
      if (in.questions() != null) {
        List<Question> next = questions(in.questions());
        if (before.responses() > 0 && !next.equals(before.questions())) {
          throw ApiException.conflict(
              "has_responses",
              "This survey already has responses. Make a copy to change the questions.");
        }
        qs = next;
      }
      jdbc.sql(
              "update survey set title = ?, description = ?, questions = ?::jsonb, is_open = ?, anonymous = ?,"
                  + " closes_on = ?, updated_at = now() where id = ?")
          .params(
              in.title() == null ? before.title() : required(in.title(), 160, "Title"),
              in.description() == null
                  ? before.description()
                  : optional(in.description(), 2000, "Description"),
              write(qs),
              in.open() == null ? before.open() : in.open(),
              in.anonymous() == null ? before.anonymous() : in.anonymous(),
              in.closesOn() == null ? before.closesOn() : in.closesOn(),
              id)
          .update();
      audit.record("survey.updated", "survey", id.toString(), null, null);
      return survey(id);
    });
  }

  public void delete(UUID id) {
    entitlements.requireFeature(MODULE, "surveys");
    tx.executeWithoutResult(s -> {
      Survey sv = survey(id);
      jdbc.sql("delete from survey where id = ?").param(id).update();
      audit.record("survey.deleted", "survey", id.toString(), Map.of("title", sv.title()), null);
    });
  }

  public Results results(UUID id) {
    entitlements.requireFeature(MODULE, "surveys");
    Survey s = survey(id);
    List<JsonNode> answers =
        jdbc.sql(
                "select answers::text from survey_response where survey_id = ? order by created_at desc")
            .param(id)
            .query((rs, i) -> read(rs.getString(1)))
            .list();
    return new Results(s, s.questions().stream().map(q -> aggregate(q, answers)).toList());
  }

  static QuestionResult aggregate(Question q, List<JsonNode> answers) {
    long answered = 0;
    double sum = 0;
    Map<String, Long> counts = new LinkedHashMap<>();
    List<String> texts = new ArrayList<>();
    if ("RATING".equals(q.type())) {
      for (int r = 1; r <= 5; r++) {
        counts.put(String.valueOf(r), 0L);
      }
    } else if (!"TEXT".equals(q.type())) {
      q.options().forEach(o -> counts.put(o, 0L));
    }
    for (JsonNode a : answers) {
      JsonNode v = a.get(q.id());
      if (v == null
          || v.isNull()
          || (v.isTextual() && v.asText().isBlank())
          || (v.isArray() && v.isEmpty())) {
        continue;
      }
      answered++;
      switch (q.type()) {
        case "RATING" -> {
          sum += v.asInt();
          counts.merge(String.valueOf(v.asInt()), 1L, Long::sum);
        }
        case "CHOICE" -> counts.merge(v.asText(), 1L, Long::sum);
        case "MULTI" -> v.forEach(x -> counts.merge(x.asText(), 1L, Long::sum));
        default -> {
          if (texts.size() < 200) {
            texts.add(v.asText());
          }
        }
      }
    }
    Double avg =
        "RATING".equals(q.type()) && answered > 0 ? Math.round(sum * 10.0 / answered) / 10.0 : null;
    return new QuestionResult(
        q.id(),
        q.type(),
        q.label(),
        answered,
        avg,
        "TEXT".equals(q.type()) ? null : counts,
        "TEXT".equals(q.type()) ? texts : null);
  }

  public String csv(UUID id) {
    entitlements.requireFeature(MODULE, "surveys");
    Survey s = survey(id);
    StringBuilder out = new StringBuilder("Submitted");
    if (!s.anonymous()) {
      out.append(",Name,Phone");
    }
    s.questions().forEach(q -> out.append(',').append(Inputs.cell(q.label())));
    out.append('\n');
    jdbc.sql(
            "select created_at, respondent_name, phone, answers::text from survey_response where survey_id = ?"
                + " order by created_at")
        .param(id)
        .query((rs, i) -> {
          JsonNode a = read(rs.getString(4));
          out.append(
              rs.getTimestamp(1).toInstant().atZone(Inputs.IST).toLocalDateTime().withNano(0));
          if (!s.anonymous()) {
            out.append(',')
                .append(Inputs.cell(rs.getString(2)))
                .append(',')
                .append(Inputs.cell(rs.getString(3)));
          }
          for (Question q : s.questions()) {
            JsonNode v = a.get(q.id());
            String text =
                v == null || v.isNull()
                    ? ""
                    : v.isArray()
                        ? String.join(
                            "; ", json.convertValue(v, new TypeReference<List<String>>() {}))
                        : v.asText();
            out.append(',').append(Inputs.cell(text));
          }
          out.append('\n');
          return null;
        })
        .list();
    return out.toString();
  }

  // ------------------------------------------------------------------ public

  public PublicSurvey publicSurvey(String host, String slug) {
    Organization org = resolver.resolve(host).orElseThrow(() -> ApiException.notFound("School"));
    return TenantContext.callInTenant(
        org.id(),
        () -> {
          Survey s = openSurvey(slug);
          return new PublicSurvey(s.title(), s.description(), s.questions(), s.anonymous());
        });
  }

  public void submit(String host, String slug, JsonNode in, String clientIp) {
    Organization org = resolver.resolve(host).orElseThrow(() -> ApiException.notFound("School"));
    String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
    if (!limiter.tryAcquire(
        "survey:" + org.id() + ":" + slug + ":" + ip, 5, Duration.ofMinutes(30))) {
      throw ApiException.tooManyRequests();
    }
    if (in == null || !in.isObject()) {
      throw ApiException.badRequest("invalid_response", "Please answer the questions.");
    }
    if (!in.path("website").asText("").isBlank()) {
      return; // honeypot
    }
    TenantContext.callInTenant(
        org.id(),
        () -> {
          Survey s = openSurvey(slug);
          ObjectNode answers = validateAnswers(s.questions(), in.path("answers"), json);
          String name = s.anonymous() ? null : Inputs.text(in, "name", 120, false, "Name");
          String phone = null;
          if (!s.anonymous() && in.hasNonNull("phone") && !in.get("phone").asText().isBlank()) {
            phone = Inputs.phone(in.get("phone").asText());
          }
          String p = phone;
          tx.executeWithoutResult(st ->
              jdbc.sql(
                      "insert into survey_response (organization_id, survey_id, answers, respondent_name, phone)"
                          + " values (?, ?, ?::jsonb, ?, ?)")
                  .params(org.id(), s.id(), answers.toString(), name, p)
                  .update());
          return null;
        });
  }

  /** Keeps only answers to known questions, checked against each question's type. */
  static ObjectNode validateAnswers(List<Question> questions, JsonNode given, ObjectMapper json) {
    ObjectNode out = json.createObjectNode();
    for (Question q : questions) {
      JsonNode v = given == null ? null : given.get(q.id());
      boolean empty =
          v == null
              || v.isNull()
              || (v.isTextual() && v.asText().isBlank())
              || (v.isArray() && v.isEmpty());
      if (empty) {
        if (q.required()) {
          throw ApiException.badRequest("missing_answer", "Please answer: " + q.label());
        }
        continue;
      }
      switch (q.type()) {
        case "RATING" -> {
          int r = v.asInt(0);
          if (r < 1 || r > 5) {
            throw ApiException.badRequest(
                "invalid_answer", "Choose a rating from 1 to 5 for: " + q.label());
          }
          out.put(q.id(), r);
        }
        case "CHOICE" -> {
          if (!q.options().contains(v.asText())) {
            throw ApiException.badRequest("invalid_answer", "Choose an option for: " + q.label());
          }
          out.put(q.id(), v.asText());
        }
        case "MULTI" -> {
          if (!v.isArray()) {
            throw ApiException.badRequest("invalid_answer", "Choose options for: " + q.label());
          }
          var arr = out.putArray(q.id());
          Set<String> seen = new HashSet<>();
          for (JsonNode x : v) {
            if (!q.options().contains(x.asText())) {
              throw ApiException.badRequest("invalid_answer", "Choose options for: " + q.label());
            }
            if (seen.add(x.asText())) {
              arr.add(x.asText());
            }
          }
        }
        default -> {
          String t = v.asText().trim();
          if (t.length() > 2000) {
            throw ApiException.badRequest("too_long", "Your answer is too long: " + q.label());
          }
          out.put(q.id(), t);
        }
      }
    }
    return out;
  }

  private Survey openSurvey(String slug) {
    if (!entitlements.hasFeature(MODULE, "surveys")
        || slug == null
        || !SLUG.matcher(slug).matches()) {
      throw ApiException.notFound("Survey");
    }
    Survey s =
        jdbc.sql(SURVEY_SELECT + " where s.slug = ?")
            .param(slug)
            .query(this::map)
            .optional()
            .orElseThrow(() -> ApiException.notFound("Survey"));
    if (!s.open() || (s.closesOn() != null && s.closesOn().isBefore(Inputs.today()))) {
      throw ApiException.notFound("Survey");
    }
    return s;
  }

  // ------------------------------------------------------------------ helpers

  /** Validates questions and gives each a stable id (q1, q2…) when missing. */
  static List<Question> questions(List<Question> in) {
    if (in == null || in.isEmpty()) {
      throw ApiException.badRequest("missing_questions", "Add at least one question.");
    }
    if (in.size() > 30) {
      throw ApiException.badRequest("too_many", "Up to 30 questions.");
    }
    List<Question> out = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    int n = 1;
    for (Question q : in) {
      String type = Inputs.oneOf(q.type(), TYPES, "question type");
      String label = required(q.label(), 300, "Question");
      List<String> options = List.of();
      if ("CHOICE".equals(type) || "MULTI".equals(type)) {
        options =
            (q.options() == null ? List.<String>of() : q.options())
                .stream()
                    .filter(o -> o != null && !o.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        if (options.size() < 2
            || options.size() > 12
            || options.stream().anyMatch(o -> o.length() > 100)) {
          throw ApiException.badRequest(
              "invalid_options", "Give 2–12 options (up to 100 characters) for: " + label);
        }
      }
      String id = q.id() != null && q.id().matches("^[a-z0-9_]{1,20}$") ? q.id() : null;
      while (id == null || ids.contains(id)) {
        id = "q" + n++;
      }
      ids.add(id);
      out.add(new Question(id, type, label, options, q.required()));
    }
    return out;
  }

  static String slugOf(String title) {
    String s =
        title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    if (s.length() > 50) {
      s = s.substring(0, 50).replaceAll("-+$", "");
    }
    return s.length() < 3 ? "survey-" + Inputs.code(4).toLowerCase(Locale.ROOT) : s;
  }

  private static final String SURVEY_SELECT =
      "select s.*, (select count(*) from survey_response r where r.survey_id = s.id) responses from survey s";

  private Survey survey(UUID id) {
    return jdbc.sql(SURVEY_SELECT + " where s.id = ?")
        .param(id)
        .query(this::map)
        .optional()
        .orElseThrow(() -> ApiException.notFound("Survey"));
  }

  private Survey map(ResultSet rs, int i) throws SQLException {
    List<Question> qs;
    try {
      qs = json.readValue(rs.getString("questions"), new TypeReference<List<Question>>() {});
    } catch (Exception e) {
      throw new SQLException("Invalid survey questions", e);
    }
    return new Survey(
        rs.getObject("id", UUID.class),
        rs.getString("slug"),
        rs.getString("title"),
        rs.getString("description"),
        qs,
        rs.getBoolean("is_open"),
        rs.getBoolean("anonymous"),
        rs.getObject("closes_on", LocalDate.class),
        rs.getLong("responses"),
        rs.getTimestamp("created_at").toInstant());
  }

  private JsonNode read(String s) {
    try {
      return json.readTree(s);
    } catch (Exception e) {
      return json.createObjectNode();
    }
  }

  private String write(Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
