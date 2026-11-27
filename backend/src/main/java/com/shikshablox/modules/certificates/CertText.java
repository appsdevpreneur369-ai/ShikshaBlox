package com.shikshablox.modules.certificates;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text helpers for certificates: merge fields, dates in words, default templates, TC fields. */
final class CertText {

  private CertText() {}

  private static final Pattern VAR = Pattern.compile("\\{\\{\\s*([a-z_.]+)\\s*}}");
  static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

  record Field(String key, String label) {}

  /** The rows of a transfer certificate, in the usual (CBSE-style) order. */
  static final List<Field> TC_FIELDS =
      List.of(
          new Field("name", "Name of the pupil"),
          new Field("mother", "Mother's name"),
          new Field("father", "Father's / guardian's name"),
          new Field("nationality", "Nationality"),
          new Field("dob", "Date of birth (in figures)"),
          new Field("dob_words", "Date of birth (in words)"),
          new Field("admission", "Date of first admission in the school"),
          new Field("last_class", "Class in which the pupil last studied"),
          new Field("last_exam", "Last annual examination taken, with result"),
          new Field("failed", "Whether failed; if so, once or twice in the same class"),
          new Field("subjects", "Subjects studied"),
          new Field("promotion", "Whether qualified for promotion to the higher class"),
          new Field("dues", "Month up to which school dues have been paid"),
          new Field("concession", "Any fee concession availed"),
          new Field("working_days", "Total working days in the academic year"),
          new Field("present_days", "Total days present"),
          new Field("ncc", "NCC cadet / Scout / Guide"),
          new Field("games", "Games played or extra-curricular activities"),
          new Field("conduct", "General conduct"),
          new Field("application_date", "Date of application for the certificate"),
          new Field("leaving_date", "Date on which the pupil's name was struck off"),
          new Field("reason", "Reason for leaving the school"),
          new Field("remarks", "Any other remarks"));

  /** kind, name, title, body, prefix, parent may request. */
  static final List<String[]> DEFAULTS =
      List.of(
          new String[] {
            "TC",
            "Transfer certificate",
            "Transfer Certificate",
            "Certified that the above information is in accordance with the school register.",
            "TC/",
            "false"
          },
          new String[] {
            "BONAFIDE",
            "Bonafide certificate",
            "Bonafide Certificate",
            "This is to certify that {{student.name}}, {{student.relation}} {{guardian.name}}, admission number"
                + " {{student.admission_no}}, is a bonafide student of {{school.name}}, studying in {{student.class}}"
                + " in the academic year {{year}}. {{student.possessive}} date of birth as per the school register is"
                + " {{student.dob}}.\n\nThis certificate is issued on request for the purpose of {{purpose}}.",
            "BC/",
            "true"
          },
          new String[] {
            "CHARACTER",
            "Character certificate",
            "Character Certificate",
            "This is to certify that {{student.name}}, {{student.relation}} {{guardian.name}}, admission number"
                + " {{student.admission_no}}, has been a student of {{school.name}} since {{student.admission_date}}"
                + " and is in {{student.class}}.\n\nTo the best of our knowledge, {{student.possessive_lower}} conduct and"
                + " character have been {{fields.conduct}}. We wish {{student.object}} every success.",
            "CC/",
            "true"
          },
          new String[] {
            "STUDY",
            "Study certificate",
            "Study Certificate",
            "This is to certify that {{student.name}}, {{student.relation}} {{guardian.name}}, admission number"
                + " {{student.admission_no}}, studied in {{school.name}} from {{student.admission_date}} and is"
                + " studying in {{student.class}} in the academic year {{year}}. {{student.possessive}} date of birth as"
                + " per the school register is {{student.dob}}.",
            "SC/",
            "true"
          });

  /** Replaces {{fields}}; unknown fields become a visible blank line to fill by hand. */
  static String render(String body, Map<String, String> vars) {
    Matcher m = VAR.matcher(body);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      String v = vars.get(m.group(1));
      m.appendReplacement(sb, Matcher.quoteReplacement(v == null || v.isBlank() ? "________" : v));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  static String date(LocalDate d) {
    return d == null ? "" : DATE.format(d);
  }

  private static final String[] ONES = {
    "",
    "One",
    "Two",
    "Three",
    "Four",
    "Five",
    "Six",
    "Seven",
    "Eight",
    "Nine",
    "Ten",
    "Eleven",
    "Twelve",
    "Thirteen",
    "Fourteen",
    "Fifteen",
    "Sixteen",
    "Seventeen",
    "Eighteen",
    "Nineteen"
  };
  private static final String[] TENS = {
    "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
  };
  private static final String[] ORDINAL = {
    "",
    "First",
    "Second",
    "Third",
    "Fourth",
    "Fifth",
    "Sixth",
    "Seventh",
    "Eighth",
    "Ninth",
    "Tenth",
    "Eleventh",
    "Twelfth",
    "Thirteenth",
    "Fourteenth",
    "Fifteenth",
    "Sixteenth",
    "Seventeenth",
    "Eighteenth",
    "Nineteenth",
    "Twentieth"
  };

  /** 28 Oct 2020 → "Twenty-Eighth October Two Thousand Twenty". */
  static String dateInWords(LocalDate d) {
    if (d == null) {
      return "";
    }
    int day = d.getDayOfMonth();
    String dayWords =
        day <= 20
            ? ORDINAL[day]
            : day == 30 ? "Thirtieth" : TENS[day / 10] + "-" + ORDINAL[day % 10];
    String month = d.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
    return dayWords + " " + month + " " + number(d.getYear());
  }

  static String number(int n) {
    if (n == 0) {
      return "Zero";
    }
    StringBuilder b = new StringBuilder();
    if (n >= 1000) {
      b.append(ONES[n / 1000]).append(" Thousand");
      n %= 1000;
    }
    if (n >= 100) {
      b.append(b.isEmpty() ? "" : " ").append(ONES[n / 100]).append(" Hundred");
      n %= 100;
    }
    if (n > 0) {
      b.append(b.isEmpty() ? "" : " ");
      b.append(n < 20 ? ONES[n] : TENS[n / 10] + (n % 10 == 0 ? "" : "-" + ONES[n % 10]));
    }
    return b.toString();
  }
}
