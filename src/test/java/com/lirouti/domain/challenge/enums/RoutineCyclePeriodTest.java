package com.lirouti.domain.challenge.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 주기 구간 계산.
 *
 * <p><b>주가 일요일에 시작한다는 것이 이 테스트의 핵심이다.</b> 기획 확정 사항이지만 Java 의
 * 기본값(ISO, 월요일 시작)과 달라서, 표준을 그대로 쓰면 <b>일요일에만 틀린다.</b> 일곱 요일 중
 * 하나에서만 어긋나는 버그는 발견이 늦고, 발견해도 원인을 짚기 어렵다.
 *
 * <p>그래서 경계를 날짜로 못 박는다. 기준으로 삼은 주는 2026-08-02(일) ~ 2026-08-08(토)이며,
 * 그 앞뒤 하루가 다른 구간으로 갈리는지까지 본다.
 */
@DisplayName("주기 구간 계산 테스트")
class RoutineCyclePeriodTest {

    @ParameterizedTest
    @CsvSource({
            // 2026-08-02 는 일요일이다. 그 주는 8/2(일) ~ 8/8(토)
            "2026-08-02, 2026-08-02",   // 일요일 자신이 시작일
            "2026-08-03, 2026-08-02",   // 월
            "2026-08-05, 2026-08-02",   // 수
            "2026-08-08, 2026-08-02",   // 토 — 아직 같은 주
            "2026-08-09, 2026-08-09",   // 다음 일요일 → 새 구간
            "2026-08-01, 2026-07-26",   // 토(전주) → 이전 구간
    })
    @DisplayName("주간 구간은 일요일에 시작한다 — ISO(월요일 시작)가 아니다")
    void weekly_StartsOnSunday(String date, String expectedStart) {
        assertThat(RoutineCycle.WEEKLY.currentPeriodStart(LocalDate.parse(date)))
                .isEqualTo(LocalDate.parse(expectedStart));
    }

    @Test
    @DisplayName("토요일과 그다음 일요일은 다른 주다 — 여기가 어긋나면 일요일만 틀린다")
    void weekly_SaturdayAndNextSunday_AreDifferentPeriods() {
        LocalDate saturday = LocalDate.parse("2026-08-08");
        LocalDate sunday = LocalDate.parse("2026-08-09");

        assertAll(
                () -> assertThat(saturday.getDayOfWeek().getValue()).isEqualTo(6),
                () -> assertThat(RoutineCycle.WEEKLY.isSamePeriod(saturday, sunday)).isFalse(),
                // 반대로 일요일과 그 주 토요일은 같은 주다
                () -> assertThat(RoutineCycle.WEEKLY.isSamePeriod(
                        LocalDate.parse("2026-08-02"), saturday)).isTrue()
        );
    }

    @Test
    @DisplayName("일간 구간은 그날 하루다")
    void daily_IsTheDayItself() {
        LocalDate day = LocalDate.parse("2026-08-05");

        assertAll(
                () -> assertThat(RoutineCycle.DAILY.currentPeriodStart(day)).isEqualTo(day),
                () -> assertThat(RoutineCycle.DAILY.isSamePeriod(day, day)).isTrue(),
                () -> assertThat(RoutineCycle.DAILY.isSamePeriod(day, day.minusDays(1))).isFalse()
        );
    }

    @Test
    @DisplayName("월간 구간은 1일에 시작하고 말일까지다")
    void monthly_StartsOnFirstDay() {
        assertAll(
                () -> assertThat(RoutineCycle.MONTHLY.currentPeriodStart(LocalDate.parse("2026-08-31")))
                        .isEqualTo(LocalDate.parse("2026-08-01")),
                () -> assertThat(RoutineCycle.MONTHLY.isSamePeriod(
                        LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"))).isTrue(),
                // 말일과 다음 달 1일은 다른 구간
                () -> assertThat(RoutineCycle.MONTHLY.isSamePeriod(
                        LocalDate.parse("2026-08-31"), LocalDate.parse("2026-09-01"))).isFalse()
        );
    }

    @ParameterizedTest
    @CsvSource({
            // 직전 주는 정확히 7일 앞의 일요일이다.
            "2026-08-05, 2026-07-26",   // 수(8/2 주) → 직전 주는 7/26
            "2026-08-02, 2026-07-26",   // 그 주 첫날에서도 같다
            "2026-08-09, 2026-08-02",
    })
    @DisplayName("주간 직전 구간은 7일 앞 일요일이다")
    void weekly_previousPeriodStart(String date, String expected) {
        assertThat(RoutineCycle.WEEKLY.previousPeriodStart(LocalDate.parse(date)))
                .isEqualTo(LocalDate.parse(expected));
    }

    @ParameterizedTest
    @CsvSource({
            // 달은 길이가 제각각이라 날짜 빼기로는 안 된다. 1일로 맞춘 뒤 한 달을 뺀다.
            "2026-03-15, 2026-02-01",   // 3월의 직전은 28일짜리 2월
            "2026-03-01, 2026-02-01",
            "2026-01-10, 2025-12-01",   // 해를 넘는다
            "2026-05-31, 2026-04-01",   // 31일 → 30일짜리 달로 가도 안전하다
    })
    @DisplayName("월간 직전 구간은 지난 달 1일이다 — 달 길이가 달라도 어긋나지 않는다")
    void monthly_previousPeriodStart(String date, String expected) {
        assertThat(RoutineCycle.MONTHLY.previousPeriodStart(LocalDate.parse(date)))
                .isEqualTo(LocalDate.parse(expected));
    }

    @Test
    @DisplayName("일간 직전 구간은 어제다")
    void daily_previousPeriodStart() {
        assertThat(RoutineCycle.DAILY.previousPeriodStart(LocalDate.parse("2026-08-05")))
                .isEqualTo(LocalDate.parse("2026-08-04"));
    }

    @Test
    @DisplayName("같은 구간은 직전 구간이 아니다 — 이미 인증한 구간에 또 해도 연속이 늘면 안 된다")
    void isPreviousPeriod_SamePeriodIsFalse() {
        LocalDate monday = LocalDate.parse("2026-08-03");
        LocalDate wednesday = LocalDate.parse("2026-08-05");

        assertAll(
                () -> assertThat(RoutineCycle.WEEKLY.isSamePeriod(monday, wednesday)).isTrue(),
                () -> assertThat(RoutineCycle.WEEKLY.isPreviousPeriod(monday, wednesday)).isFalse()
        );
    }

    @Test
    @DisplayName("주 경계를 하루 넘으면 직전 구간이 된다 — 토→일이 연속의 갈림길이다")
    void isPreviousPeriod_AcrossWeekBoundary() {
        LocalDate saturday = LocalDate.parse("2026-08-08");
        LocalDate sunday = LocalDate.parse("2026-08-09");

        assertAll(
                () -> assertThat(RoutineCycle.WEEKLY.isSamePeriod(saturday, sunday)).isFalse(),
                () -> assertThat(RoutineCycle.WEEKLY.isPreviousPeriod(saturday, sunday)).isTrue(),
                // 두 주를 건너뛰면 끊긴 것이다
                () -> assertThat(RoutineCycle.WEEKLY
                        .isPreviousPeriod(saturday, LocalDate.parse("2026-08-16"))).isFalse()
        );
    }
}
