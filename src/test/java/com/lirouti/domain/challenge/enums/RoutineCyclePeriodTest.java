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
}
