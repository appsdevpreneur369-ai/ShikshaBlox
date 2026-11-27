package com.shikshablox.modules.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shikshablox.platform.shared.web.ApiException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GeneratorTest {

  private static final List<Integer> DAYS = List.of(1, 2, 3, 4, 5, 6);
  private static final List<Integer> PERIODS = List.of(0, 1, 2, 4, 5, 7, 8, 9);

  @Test
  void fillsLoadsWithoutClashesAndSpreadsSubjects() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID teacher = UUID.randomUUID();
    UUID eng = UUID.randomUUID();
    UUID math = UUID.randomUUID();
    UUID math2 = UUID.randomUUID();
    List<Generator.Load> loads =
        List.of(
            new Generator.Load(a, eng, teacher, 7),
            new Generator.Load(a, math, null, 8),
            new Generator.Load(b, math2, teacher, 8));
    Generator.Result r =
        Generator.generate(
            loads,
            DAYS,
            PERIODS,
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            2,
            10);
    assertThat(r.unplaced()).isEmpty();
    assertThat(r.placed()).hasSize(23);
    Set<String> teacherSlots = new HashSet<>();
    Set<String> sectionSlots = new HashSet<>();
    Map<String, Integer> perDay = new HashMap<>();
    for (Generator.Placement p : r.placed()) {
      if (teacher.equals(p.teacher())) {
        assertThat(teacherSlots.add(p.day() + ":" + p.period()))
            .as("teacher double-booked")
            .isTrue();
      }
      assertThat(sectionSlots.add(p.section() + ":" + p.day() + ":" + p.period()))
          .as("section double-booked")
          .isTrue();
      perDay.merge(p.section() + ":" + p.subject() + ":" + p.day(), 1, Integer::sum);
    }
    assertThat(perDay.values()).allMatch(n -> n <= 2);
  }

  @Test
  void keepsExistingSlotsAndReportsWhatDoesNotFit() {
    UUID a = UUID.randomUUID();
    UUID eng = UUID.randomUUID();
    Map<UUID, Set<String>> busy = new HashMap<>();
    busy.put(a, new HashSet<>(Set.of("1:0")));
    // 1 day x 2 periods, one already used, max 2 a day: 3 lessons can't all fit.
    Generator.Result r =
        Generator.generate(
            List.of(new Generator.Load(a, eng, null, 3)),
            List.of(1),
            List.of(0, 1),
            busy,
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            2,
            5);
    assertThat(r.placed()).hasSize(1);
    assertThat(r.placed().get(0).period()).isEqualTo(1);
    assertThat(r.unplaced()).hasSize(2);
  }

  @Test
  void bellScheduleIsValidated() {
    assertThat(
            TimetableService.cleanPeriods(
                List.of(
                    new TimetableService.Period("P1", "08:30", "09:10", false),
                    new TimetableService.Period("Break", "09:10", "09:20", true))))
        .hasSize(2);
    assertThatThrownBy(() ->
            TimetableService.cleanPeriods(
                List.of(new TimetableService.Period("P1", "09:10", "08:30", false))))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() ->
            TimetableService.cleanPeriods(
                List.of(
                    new TimetableService.Period("P1", "08:30", "09:10", false),
                    new TimetableService.Period("P2", "09:00", "09:40", false))))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() ->
            TimetableService.cleanPeriods(
                List.of(new TimetableService.Period("Lunch", "12:00", "12:30", true))))
        .isInstanceOf(ApiException.class);
  }
}
