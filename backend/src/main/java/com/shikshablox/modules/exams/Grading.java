package com.shikshablox.modules.exams;

import com.shikshablox.platform.shared.web.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure marks arithmetic: components, totals, percentages, grade bands, pass rule, weights. */
final class Grading {

  private Grading() {}

  record Component(String key, String label, double max) {}

  record Band(double min, String grade, double point) {}

  /** A subject's result in one exam. {@code percent} is null when excused (ML) or not entered. */
  record Result(Double total, double max, Double percent, String grade, String code) {}

  static double maxTotal(List<Component> components) {
    return components.stream().mapToDouble(Component::max).sum();
  }

  /** Checks entered marks against the components: 0..max, steps of 0.5, known keys only. */
  static Map<String, Double> validate(
      Map<String, Double> marks, List<Component> components, String who) {
    Map<String, Double> out = new LinkedHashMap<>();
    if (marks == null) {
      return out;
    }
    for (Map.Entry<String, Double> e : marks.entrySet()) {
      Component c =
          components.stream().filter(x -> x.key().equals(e.getKey())).findFirst().orElse(null);
      if (c == null) {
        throw ApiException.badRequest("invalid_component", "Unknown part " + e.getKey() + ".");
      }
      Double v = e.getValue();
      if (v == null) {
        continue;
      }
      if (v < 0 || v > c.max() || Math.round(v * 2) != v * 2) {
        throw ApiException.badRequest(
            "invalid_marks",
            who
                + ": "
                + c.label()
                + " must be between 0 and "
                + fmt(c.max())
                + " (halves allowed).");
      }
      out.put(c.key(), v);
    }
    return out;
  }

  /** AB counts as zero; ML (medical) is left out of totals; partly entered marks show no result. */
  static Result result(
      Map<String, Double> marks, String code, List<Component> components, List<Band> bands) {
    double max = maxTotal(components);
    if ("ML".equals(code)) {
      return new Result(null, max, null, null, "ML");
    }
    if ("AB".equals(code)) {
      return new Result(0.0, max, 0.0, grade(0, bands), "AB");
    }
    if (marks == null || marks.isEmpty() || marks.size() < components.size()) {
      return new Result(null, max, null, null, null);
    }
    double total = marks.values().stream().mapToDouble(Double::doubleValue).sum();
    double pct = max == 0 ? 0 : round1(total * 100 / max);
    return new Result(total, max, pct, grade(pct, bands), null);
  }

  static String grade(double percent, List<Band> bands) {
    for (Band b : bands) {
      if (percent >= b.min()) {
        return b.grade();
      }
    }
    return bands.isEmpty() ? null : bands.get(bands.size() - 1).grade();
  }

  /** Overall % of an exam over the subjects with a result (ML subjects excluded). */
  static Double overall(List<Result> results) {
    double t = 0;
    double m = 0;
    for (Result r : results) {
      if (r.total() != null) {
        t += r.total();
        m += r.max();
      }
    }
    return m == 0 ? null : round1(t * 100 / m);
  }

  /** Passed when every subject with a result is at or above the pass mark (AB fails). */
  static Boolean passed(List<Result> results, int passPercent) {
    boolean any = false;
    for (Result r : results) {
      if (r.percent() != null) {
        any = true;
        if (r.percent() < passPercent) {
          return false;
        }
      }
    }
    return any ? true : null;
  }

  /** Weighted % across exams: weights of exams without a result are left out. */
  static Double weighted(List<Double> percents, List<Integer> weights) {
    double sum = 0;
    double w = 0;
    for (int i = 0; i < percents.size(); i++) {
      Double p = percents.get(i);
      int wt = weights.get(i);
      if (p != null && wt > 0) {
        sum += p * wt;
        w += wt;
      }
    }
    return w == 0 ? null : round1(sum / w);
  }

  /** Components from a request: 1 to 4 parts, keys like "TH", labels, max 1..200. */
  static List<Component> components(List<Map<String, Object>> in) {
    if (in == null || in.isEmpty()) {
      return List.of(new Component("M", "Marks", 100));
    }
    if (in.size() > 4) {
      throw ApiException.badRequest("too_many_parts", "Use up to 4 parts per exam.");
    }
    List<Component> out = new ArrayList<>();
    for (Map<String, Object> m : in) {
      String label = m.get("label") == null ? "" : m.get("label").toString().trim();
      if (label.isEmpty() || label.length() > 40) {
        throw ApiException.badRequest(
            "invalid_part", "Give each part a name (up to 40 characters).");
      }
      double max;
      try {
        max = Double.parseDouble(String.valueOf(m.get("max")));
      } catch (NumberFormatException e) {
        throw ApiException.badRequest("invalid_part", "Give each part its maximum marks.");
      }
      if (max < 1 || max > 200 || Math.round(max * 2) != max * 2) {
        throw ApiException.badRequest("invalid_part", "Maximum marks must be 1 to 200.");
      }
      String key =
          m.get("key") == null
              ? ""
              : m.get("key").toString().trim().toUpperCase(java.util.Locale.ROOT);
      if (!key.matches("^[A-Z]{1,4}$")) {
        key = "P" + (out.size() + 1);
      }
      String k = key;
      if (out.stream().anyMatch(c -> c.key().equals(k))) {
        key = "P" + (out.size() + 1);
      }
      out.add(new Component(key, label, max));
    }
    return out;
  }

  static double round1(double v) {
    return Math.round(v * 10) / 10.0;
  }

  static String fmt(double v) {
    return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
  }
}
