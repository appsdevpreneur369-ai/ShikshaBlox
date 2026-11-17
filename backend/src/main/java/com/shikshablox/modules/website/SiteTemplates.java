package com.shikshablox.modules.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The 14 standard pages every school gets (on every plan; they do not count toward the custom page
 * limit), with sensible starter content built from the onboarding details. Schools edit everything
 * afterwards; nothing here is legal or board-mandated text.
 */
@Component
public class SiteTemplates {

  /** A standard page definition. */
  public record StandardPage(String key, String slug, String title, int order) {}

  public static final List<StandardPage> STANDARD_PAGES =
      List.of(
          new StandardPage("home", "", "Home", 0),
          new StandardPage("about", "about", "About Us", 10),
          new StandardPage("principal", "principal-message", "Principal's Message", 20),
          new StandardPage("vision", "vision-mission", "Vision & Mission", 30),
          new StandardPage("academics", "academics", "Academics", 40),
          new StandardPage("admissions", "admissions", "Admissions", 50),
          new StandardPage("facilities", "facilities", "Facilities", 60),
          new StandardPage("faculty", "faculty", "Faculty", 70),
          new StandardPage("events", "events", "Events", 80),
          new StandardPage("gallery", "gallery", "Gallery", 90),
          new StandardPage("news", "news", "News & Circulars", 100),
          new StandardPage("contact", "contact", "Contact Us", 110),
          new StandardPage("careers", "careers", "Careers", 120),
          new StandardPage("faqs", "faqs", "FAQs", 130));

  private final ObjectMapper json;

  SiteTemplates(ObjectMapper json) {
    this.json = json;
  }

  /** Context used to personalise the starter content. */
  public record SchoolInfo(String name, String city, String board, String tagline) {
    String place() {
      return city == null || city.isBlank() ? "" : " in " + city;
    }

    String boardLabel() {
      return board == null || board.isBlank() ? "" : board + " ";
    }
  }

  // ------------------------------------------------------------------------- documents

  public JsonNode document(String key, SchoolInfo s) {
    Doc d = new Doc();
    switch (key) {
      case "home" -> {
        d.hero(
            "split",
            "Welcome to " + s.name(),
            s.tagline() != null && !s.tagline().isBlank()
                ? s.tagline()
                : "Nurturing curious minds and kind hearts",
            "A "
                + s.boardLabel()
                + "school"
                + s.place()
                + " where every child is known, valued and encouraged to grow.",
            link("Admissions", "/admissions"),
            link("Discover our school", "/about"));
        d.imageText(
            "Why families choose us",
            "A school that brings out the best in every child",
            "<p>We combine strong academics with care for every child's character, curiosity and confidence. Small classes, dedicated teachers and a safe campus help students learn deeply and grow into capable, compassionate young people.</p>",
            "right",
            link("About us", "/about"));
        d.cards(
            "Learning at " + s.name(),
            null,
            3,
            List.of(
                card(
                    "Academics",
                    "A balanced curriculum with strong foundations in language, mathematics and science.",
                    "/academics"),
                card(
                    "Facilities",
                    "Bright classrooms, library, labs and safe spaces to play and explore.",
                    "/facilities"),
                card(
                    "Life at school",
                    "Events, celebrations, sports and the arts throughout the year.",
                    "/events")));
        d.noticesFeed("Latest notices", 4);
        d.eventsFeed("Upcoming events", 3, "upcoming");
        d.galleryStrip("Moments from our campus", null);
        d.cta(
            "Admissions are open",
            "Visit us, meet our teachers and see how your child can thrive here.",
            link("Enquire now", "/admissions#enquire"),
            link("Contact us", "/contact"));
      }
      case "about" -> {
        d.hero(
            "centered",
            "About " + s.name(),
            "Our story",
            "Who we are, what we believe and how we care for every child.",
            null,
            null);
        d.imageText(
            "Our story",
            "Rooted in community",
            "<p>Tell visitors how and why the school began, the community it serves and the milestones that shaped it.</p>",
            "right",
            null);
        d.cards(
            "What makes us different",
            null,
            3,
            List.of(
                card(
                    "Every child known",
                    "Small classes and caring teachers who know each student by name.",
                    null),
                card("Character and competence", "We teach values alongside academics.", null),
                card(
                    "Open to all",
                    "A welcoming school for children from every background.",
                    null)));
        d.cta(
            "Meet our principal",
            null,
            link("Read the principal's message", "/principal-message"),
            link("Our vision & mission", "/vision-mission"));
      }
      case "principal" -> {
        d.hero("centered", "Principal's Message", "From the principal's desk", null, null, null);
        d.imageText(
            null,
            "Dear parents and students,",
            "<p>Welcome to "
                + esc(s.name())
                + ". Share a warm personal message about the school's purpose, its approach to learning and what families can expect.</p><p>Warm regards,<br>Principal</p>",
            "left",
            null);
      }
      case "vision" -> {
        d.hero("centered", "Vision & Mission", "What guides us", null, null, null);
        d.cards(
            null,
            null,
            2,
            List.of(
                card(
                    "Our vision",
                    "Every child receives an education that brings out his or her best and nurtures character, curiosity and competence.",
                    null),
                card(
                    "Our mission",
                    "To provide a learning environment where students are known and valued, and equipped to grow into capable, compassionate adults.",
                    null)));
        d.cards(
            "Our values",
            null,
            4,
            List.of(
                card("Integrity", null, null),
                card("Respect", null, null),
                card("Excellence", null, null),
                card("Service", null, null)));
      }
      case "academics" -> {
        d.hero(
            "centered",
            "Academics",
            s.boardLabel() + "curriculum",
            "Strong foundations, curious minds and confident learners.",
            null,
            null);
        d.cards(
            "Stages of learning",
            null,
            3,
            List.of(
                card(
                    "Early years (Pre-K – UKG)",
                    "Learning through play, stories, music and hands-on discovery.",
                    null),
                card(
                    "Primary (Classes 1 – 5)",
                    "Reading, writing, numeracy and enquiry with lots of practice and care.",
                    null),
                card(
                    "Middle & secondary (Classes 6 – 10)",
                    "Subject depth, board preparation, projects and leadership.",
                    null)));
        d.richText(
            "<h2>Teaching approach</h2><p>Describe your teaching methods, assessment, homework policy and support for every learner.</p>");
        d.cta("Questions about academics?", null, link("Contact the school", "/contact"), null);
      }
      case "admissions" -> {
        d.hero(
            "split",
            "Admissions",
            "Join our school family",
            "We welcome applications throughout the year for all classes, subject to availability.",
            link("Enquire now", "#enquire"),
            link("Read FAQs", "/faqs"));
        d.steps(
            "How to apply",
            null,
            List.of(
                step(
                    "Submit an enquiry", "Fill in the enquiry form below and we will contact you."),
                step("Visit the school", "Tour the campus and meet our teachers."),
                step(
                    "Interaction",
                    "A friendly interaction with your child to understand how we can support them."),
                step("Confirm admission", "Complete the forms and submit the documents.")));
        d.downloads("Documents", List.of());
        d.enquiryForm(
            "Admission enquiry",
            "Share a few details and our admissions team will get back to you.",
            "enquire");
      }
      case "facilities" -> {
        d.hero(
            "centered",
            "Facilities",
            "Our campus",
            "Safe, bright and inspiring spaces to learn and play.",
            null,
            null);
        d.cards(
            null,
            null,
            3,
            List.of(
                card(
                    "Classrooms",
                    "Bright, well-ventilated classrooms with learning resources.",
                    null),
                card("Library", "Books for every reading level to build a love of reading.", null),
                card("Science & computer labs", "Hands-on experiments and digital skills.", null),
                card("Play areas", "Safe spaces for sports and free play.", null),
                card(
                    "Early childhood centre",
                    "Play-based learning equipment for our youngest learners.",
                    null),
                card("Safety", "Supervised campus with clear safety routines.", null)));
        d.galleryStrip("Take a look around", null);
      }
      case "faculty" -> {
        d.hero(
            "centered",
            "Our Faculty",
            "Dedicated teachers",
            "Caring, qualified educators who know every child.",
            null,
            null);
        d.staffGrid(null, null);
      }
      case "events" -> {
        d.hero(
            "centered",
            "Events",
            "School calendar",
            "Celebrations, competitions and activities through the year.",
            null,
            null);
        d.eventsFeed("Upcoming", 20, "upcoming");
        d.eventsFeed("Past events", 12, "past");
      }
      case "gallery" -> {
        d.hero("centered", "Gallery", "Life at school", null, null, null);
        d.albumsFeed(null, 24);
      }
      case "news" -> {
        d.hero("centered", "News & Circulars", "Stay informed", null, null, null);
        d.noticesFeed("Notices & circulars", 20);
        d.newsFeed("School news", 12);
      }
      case "contact" -> {
        d.hero("centered", "Contact Us", "We'd love to hear from you", null, null, null);
        d.contactDetails("Visit or call us");
        d.enquiryForm("Send us a message", null, "enquire");
      }
      case "careers" -> {
        d.hero(
            "centered",
            "Careers",
            "Teach with us",
            "Join a team that cares deeply about every child.",
            null,
            null);
        d.careersFeed("Current openings", "We are always happy to hear from passionate educators.");
      }
      case "faqs" -> {
        d.hero("centered", "Frequently Asked Questions", null, null, null, null);
        d.faq(
            null,
            List.of(
                Map.of(
                    "question",
                    "When do admissions open?",
                    "answer",
                    "Admissions are open throughout the year, subject to seat availability. Please submit an enquiry and we will contact you."),
                Map.of(
                    "question",
                    "What are the school timings?",
                    "answer",
                    "Please contact the school office for the current timings."),
                Map.of(
                    "question",
                    "Is transport available?",
                    "answer",
                    "Please contact the school office for details about transport.")));
        d.cta("Still have questions?", null, link("Contact us", "/contact"), null);
      }
      default -> throw new IllegalArgumentException("Unknown template " + key);
    }
    return d.build();
  }

  /** Default header and footer menus pointing at the standard pages. */
  public Map<String, ArrayNode> menus(Map<String, UUID> pageIdsByKey) {
    ArrayNode header = json.createArrayNode();
    header.add(item("Home", pageIdsByKey.get("home")));
    ObjectNode about = item("About", pageIdsByKey.get("about"));
    ArrayNode aboutChildren = about.putArray("children");
    aboutChildren.add(item("About Us", pageIdsByKey.get("about")));
    aboutChildren.add(item("Principal's Message", pageIdsByKey.get("principal")));
    aboutChildren.add(item("Vision & Mission", pageIdsByKey.get("vision")));
    aboutChildren.add(item("Faculty", pageIdsByKey.get("faculty")));
    header.add(about);
    header.add(item("Academics", pageIdsByKey.get("academics")));
    header.add(item("Admissions", pageIdsByKey.get("admissions")));
    header.add(item("Facilities", pageIdsByKey.get("facilities")));
    ObjectNode life = item("School Life", pageIdsByKey.get("events"));
    ArrayNode lifeChildren = life.putArray("children");
    lifeChildren.add(item("Events", pageIdsByKey.get("events")));
    lifeChildren.add(item("Gallery", pageIdsByKey.get("gallery")));
    lifeChildren.add(item("News & Circulars", pageIdsByKey.get("news")));
    header.add(life);
    header.add(item("Contact", pageIdsByKey.get("contact")));

    ArrayNode footer = json.createArrayNode();
    for (String k : List.of("about", "admissions", "news", "careers", "faqs", "contact")) {
      String title =
          STANDARD_PAGES.stream().filter(p -> p.key().equals(k)).findFirst().orElseThrow().title();
      footer.add(item(title, pageIdsByKey.get(k)));
    }
    Map<String, ArrayNode> out = new LinkedHashMap<>();
    out.put("header", header);
    out.put("footer", footer);
    return out;
  }

  private ObjectNode item(String label, UUID pageId) {
    ObjectNode n = json.createObjectNode();
    n.put("id", UUID.randomUUID().toString().substring(0, 8));
    n.putObject("label").put("en", label);
    n.put("pageId", pageId.toString());
    return n;
  }

  // ------------------------------------------------------------------------- builders

  private ObjectNode link(String label, String href) {
    return json.createObjectNode().put("label", label).put("href", href);
  }

  private ObjectNode stat(String value, String label) {
    return json.createObjectNode().put("value", value).put("label", label);
  }

  private ObjectNode card(String title, String text, String href) {
    ObjectNode c = json.createObjectNode().put("title", title);
    if (text != null) {
      c.put("text", text);
    }
    if (href != null) {
      c.put("href", href);
    }
    return c;
  }

  private ObjectNode step(String title, String text) {
    return json.createObjectNode().put("title", title).put("text", text);
  }

  static String esc(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  /** Fluent page-document builder (shared with seeds). */
  public final class Doc {
    private final ArrayNode blocks = json.createArrayNode();
    private int n;

    private ObjectNode add(String type, ObjectNode props) {
      ObjectNode b = blocks.addObject();
      b.put("id", type + "-" + (++n));
      b.put("type", type);
      b.set("props", props);
      return b;
    }

    private ObjectNode props() {
      return json.createObjectNode();
    }

    private static void opt(ObjectNode o, String k, String v) {
      if (v != null && !v.isBlank()) {
        o.put(k, v);
      }
    }

    public Doc hero(
        String variant,
        String heading,
        String eyebrow,
        String sub,
        ObjectNode primary,
        ObjectNode secondary) {
      return heroWithImage(variant, heading, eyebrow, sub, null, primary, secondary);
    }

    public Doc heroWithImage(
        String variant,
        String heading,
        String eyebrow,
        String sub,
        UUID imageId,
        ObjectNode primary,
        ObjectNode secondary) {
      ObjectNode p = props().put("variant", variant).put("heading", heading);
      opt(p, "eyebrow", eyebrow);
      opt(p, "subheading", sub);
      if (imageId != null) {
        p.put("imageId", imageId.toString());
      }
      if (primary != null) {
        p.set("primaryCta", primary);
      }
      if (secondary != null) {
        p.set("secondaryCta", secondary);
      }
      add("hero", p);
      return this;
    }

    public Doc richText(String html) {
      add("richText", props().put("html", html));
      return this;
    }

    public Doc imageText(
        String eyebrow, String heading, String html, String position, ObjectNode cta) {
      return imageText(eyebrow, heading, html, position, cta, null);
    }

    public Doc imageText(
        String eyebrow,
        String heading,
        String html,
        String position,
        ObjectNode cta,
        UUID imageId) {
      ObjectNode p = props().put("html", html).put("imagePosition", position);
      opt(p, "eyebrow", eyebrow);
      opt(p, "heading", heading);
      if (cta != null) {
        p.set("cta", cta);
      }
      if (imageId != null) {
        p.put("imageId", imageId.toString());
      }
      add("imageText", p);
      return this;
    }

    public Doc cards(String heading, String intro, int columns, List<ObjectNode> items) {
      ObjectNode p = props().put("columns", columns);
      opt(p, "heading", heading);
      opt(p, "intro", intro);
      p.putArray("items").addAll(items);
      add("cards", p);
      return this;
    }

    public Doc stats(String heading, List<ObjectNode> items) {
      ObjectNode p = props();
      opt(p, "heading", heading);
      p.putArray("items").addAll(items);
      add("stats", p);
      return this;
    }

    public Doc steps(String heading, String intro, List<ObjectNode> items) {
      ObjectNode p = props();
      opt(p, "heading", heading);
      opt(p, "intro", intro);
      p.putArray("items").addAll(items);
      add("steps", p);
      return this;
    }

    public Doc testimonials(String heading, List<ObjectNode> items) {
      ObjectNode p = props();
      opt(p, "heading", heading);
      p.putArray("items").addAll(items);
      add("testimonials", p);
      return this;
    }

    public Doc cta(String heading, String text, ObjectNode primary, ObjectNode secondary) {
      ObjectNode p = props().put("heading", heading);
      opt(p, "text", text);
      p.set("primary", primary);
      if (secondary != null) {
        p.set("secondary", secondary);
      }
      ObjectNode b = add("cta", p);
      b.putObject("settings").put("background", "primary");
      return this;
    }

    public Doc galleryStrip(String heading, List<UUID> imageIds) {
      ObjectNode p = props().put("layout", "masonry");
      opt(p, "heading", heading);
      ArrayNode ids = p.putArray("imageIds");
      if (imageIds != null) {
        imageIds.forEach(i -> ids.add(i.toString()));
      }
      add("galleryStrip", p);
      return this;
    }

    public Doc faq(String heading, List<Map<String, String>> items) {
      ObjectNode p = props().put("source", "inline");
      opt(p, "heading", heading);
      ArrayNode arr = p.putArray("items");
      items.forEach(
          i -> arr.addObject().put("question", i.get("question")).put("answer", i.get("answer")));
      add("faq", p);
      return this;
    }

    public Doc downloads(String heading, List<ObjectNode> items) {
      ObjectNode p = props();
      opt(p, "heading", heading);
      p.putArray("items").addAll(items);
      add("downloads", p);
      return this;
    }

    public Doc map(String heading, String query) {
      ObjectNode p = props().put("query", query).put("height", "medium");
      opt(p, "heading", heading);
      add("map", p);
      return this;
    }

    public Doc contactDetails(String heading) {
      ObjectNode p = props().put("showMap", true);
      opt(p, "heading", heading);
      add("contactDetails", p);
      return this;
    }

    public Doc noticesFeed(String heading, int limit) {
      ObjectNode p = props().put("limit", limit);
      opt(p, "heading", heading);
      add("noticesFeed", p);
      return this;
    }

    public Doc newsFeed(String heading, int limit) {
      ObjectNode p = props().put("limit", limit);
      opt(p, "heading", heading);
      add("newsFeed", p);
      return this;
    }

    public Doc eventsFeed(String heading, int limit, String show) {
      ObjectNode p = props().put("limit", limit).put("show", show);
      opt(p, "heading", heading);
      add("eventsFeed", p);
      return this;
    }

    public Doc albumsFeed(String heading, int limit) {
      ObjectNode p = props().put("limit", limit);
      opt(p, "heading", heading);
      add("albumsFeed", p);
      return this;
    }

    public Doc staffGrid(String heading, String intro) {
      ObjectNode p = props();
      opt(p, "heading", heading);
      opt(p, "intro", intro);
      add("staffGrid", p);
      return this;
    }

    public Doc careersFeed(String heading, String intro) {
      ObjectNode p = props();
      opt(p, "heading", heading);
      opt(p, "intro", intro);
      add("careersFeed", p);
      return this;
    }

    public Doc enquiryForm(String heading, String intro, String anchor) {
      ObjectNode p = props();
      opt(p, "heading", heading);
      opt(p, "intro", intro);
      ObjectNode b = add("enquiryForm", p);
      if (anchor != null) {
        b.putObject("settings").put("anchor", anchor).put("background", "muted");
      }
      return this;
    }

    public ObjectNode build() {
      ObjectNode doc = json.createObjectNode().put("schemaVersion", 1);
      doc.set("blocks", blocks);
      return doc;
    }
  }

  public Doc newDocument() {
    return new Doc();
  }

  public ObjectNode linkNode(String label, String href) {
    return link(label, href);
  }

  public ObjectNode cardNode(String title, String text, String href) {
    return card(title, text, href);
  }

  public ObjectNode statNode(String value, String label) {
    return stat(value, label);
  }

  public ObjectNode stepNode(String title, String text) {
    return step(title, text);
  }
}
