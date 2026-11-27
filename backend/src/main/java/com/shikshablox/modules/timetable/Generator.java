package com.shikshablox.modules.timetable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Fills weekly timetables from subject loads. Hard rules: a section and a teacher are in one place
 * at a time, slots already in the grid are kept, and a subject appears at most {@code maxPerDay}
 * times a day in a section. Soft rule: spread each subject across the week. Greedy placement of the
 * most constrained lessons first, repeated with different tie-breaks; the best run wins.
 */
public final class Generator {

  private Generator() {}

  public record Lesson(UUID section, UUID subject, UUID teacher) {}

  public record Load(UUID section, UUID subject, UUID teacher, int perWeek) {}

  public record Placement(UUID section, int day, int period, UUID subject, UUID teacher) {}

  public record Result(List<Placement> placed, List<Lesson> unplaced) {}

  /**
   * @param busySection slots already used per section ("day:period")
   * @param busyTeacher slots already used per teacher (any section, incl. ones not generated)
   * @param existing lessons already in the grid, per section and subject (they count towards the
   *     load)
   */
  public static Result generate(
      List<Load> loads,
      List<Integer> days,
      List<Integer> periods,
      Map<UUID, Set<String>> busySection,
      Map<UUID, Set<String>> busyTeacher,
      Map<String, Map<Integer, Integer>> existingPerDay,
      Map<String, Integer> existing,
      int maxPerDay,
      int attempts) {
    Result best = null;
    for (int a = 0; a < Math.max(1, attempts); a++) {
      Result r =
          once(
              loads,
              days,
              periods,
              busySection,
              busyTeacher,
              existingPerDay,
              existing,
              maxPerDay,
              new Random(31L * a + 7));
      if (best == null || r.unplaced().size() < best.unplaced().size()) {
        best = r;
      }
      if (best.unplaced().isEmpty()) {
        break;
      }
    }
    return best;
  }

  private static Result once(
      List<Load> loads,
      List<Integer> days,
      List<Integer> periods,
      Map<UUID, Set<String>> busySectionIn,
      Map<UUID, Set<String>> busyTeacherIn,
      Map<String, Map<Integer, Integer>> existingPerDay,
      Map<String, Integer> existing,
      int maxPerDay,
      Random rnd) {
    Map<UUID, Set<String>> sec = copy(busySectionIn);
    Map<UUID, Set<String>> tch = copy(busyTeacherIn);
    Map<String, Map<Integer, Integer>> perDay = new HashMap<>();
    existingPerDay.forEach((k, v) -> perDay.put(k, new HashMap<>(v)));
    Map<UUID, Integer> teacherLoad = new HashMap<>();
    List<Lesson> lessons = new ArrayList<>();
    for (Load l : loads) {
      int need = l.perWeek() - existing.getOrDefault(l.section() + ":" + l.subject(), 0);
      for (int i = 0; i < need; i++) {
        lessons.add(new Lesson(l.section(), l.subject(), l.teacher()));
      }
      if (l.teacher() != null) {
        teacherLoad.merge(l.teacher(), Math.max(0, need), Integer::sum);
      }
    }
    // Busiest teachers first, then a shuffle so retries explore other orders.
    java.util.Collections.shuffle(lessons, rnd);
    lessons.sort(
        Comparator.comparingInt(
            (Lesson x) -> x.teacher() == null ? 0 : -teacherLoad.get(x.teacher())));
    List<Placement> placed = new ArrayList<>();
    List<Lesson> unplaced = new ArrayList<>();
    for (Lesson l : lessons) {
      String key = l.section() + ":" + l.subject();
      Map<Integer, Integer> counts = perDay.computeIfAbsent(key, k -> new HashMap<>());
      int bestScore = Integer.MAX_VALUE;
      int[] bestSlot = null;
      for (int d : days) {
        int onDay = counts.getOrDefault(d, 0);
        if (onDay >= maxPerDay) {
          continue;
        }
        for (int p : periods) {
          String slot = d + ":" + p;
          if (sec.getOrDefault(l.section(), Set.of()).contains(slot)) {
            continue;
          }
          if (l.teacher() != null && tch.getOrDefault(l.teacher(), Set.of()).contains(slot)) {
            continue;
          }
          // Spread across days; avoid the same subject in back-to-back periods unless needed.
          int score = onDay * 100 + rnd.nextInt(10);
          if (sec.getOrDefault(l.section(), Set.of()).contains(d + ":" + (p - 1))
              && sameSubject(placed, l, d, p - 1)) {
            score += 30;
          }
          if (score < bestScore) {
            bestScore = score;
            bestSlot = new int[] {d, p};
          }
        }
      }
      if (bestSlot == null) {
        unplaced.add(l);
        continue;
      }
      String slot = bestSlot[0] + ":" + bestSlot[1];
      sec.computeIfAbsent(l.section(), k -> new HashSet<>()).add(slot);
      if (l.teacher() != null) {
        tch.computeIfAbsent(l.teacher(), k -> new HashSet<>()).add(slot);
      }
      counts.merge(bestSlot[0], 1, Integer::sum);
      placed.add(new Placement(l.section(), bestSlot[0], bestSlot[1], l.subject(), l.teacher()));
    }
    return new Result(placed, unplaced);
  }

  private static boolean sameSubject(List<Placement> placed, Lesson l, int day, int period) {
    for (Placement p : placed) {
      if (p.section().equals(l.section())
          && p.day() == day
          && p.period() == period
          && p.subject().equals(l.subject())) {
        return true;
      }
    }
    return false;
  }

  private static Map<UUID, Set<String>> copy(Map<UUID, Set<String>> in) {
    Map<UUID, Set<String>> out = new HashMap<>();
    in.forEach((k, v) -> out.put(k, new HashSet<>(v)));
    return out;
  }
}
