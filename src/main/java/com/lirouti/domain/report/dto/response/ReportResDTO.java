package com.lirouti.domain.report.dto.response;

import lombok.Builder;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * 마이 > 리포트 화면 응답.
 *
 * <p>주간(막대 그래프)·월간(달력) 두 뷰가 있지만 하단의 {@link ActivityStats}("활동 통계")는
 * 뷰와 무관하게 항상 "이번 달" 기준 한 가지 값을 보여준다 — 화면 시안에서 주간/월간을
 * 오가도 하단 카드 숫자가 그대로인 이유다.
 */
public class ReportResDTO {
    private ReportResDTO() {
    }

    /**
     * 주간 리포트, 일 - 토
     */
    @Builder
    public record Weekly(
            YearMonth displayMonth,
            int weekOfMonth,
            LocalDate weekStart,
            LocalDate weekend,
            List<DailyBar> days,
            ActivityStats stats
    ) {
    }

    /**
     * 주간 막대 그래프의 하루치 데이터.
     *
     * <p>막대 높이는 {@code completedCount / scheduledCount}로 계산한다
     * (그날 예정된 루틴이 없으면 {@code scheduledCount}는 0이고 막대도 비운다).
     *
     * @param scheduledCount 그날 예정된 루틴 수(개인 루틴 + 그룹 루틴) — 분모
     * @param completedCount 그날 완료한 루틴 수(개인 루틴 + 그룹 루틴) — 분자
     */
    @Builder
    public record DailyBar(
            LocalDate date,
            int scheduledCount,
            int completedCount,
            boolean isToday
    ) {
    }

    /**
     * 월간 리포트, 달력 형태롤 하루마다 완료 여부만 표시한다.
     */
    @Builder
    public record Monthly(
            YearMonth yearMonth,
            List<DailyMark> days,
            ActivityStats stats
    ) {
    }

    /**
     * 월간 달력의 하루치 데이터.
     *
     * <p>달력 날짜 하단 박스는 "그날 활동이 있었는가"라는 단순 여부가 아니라, 주간 막대와
     * 동일하게 {@code completedCount / scheduledCount} 비율만큼 채워 보여준다. 그날 예정된
     * 루틴이 없으면({@code scheduledCount == 0}) 박스는 채우지 않는다.
     *
     * @param scheduledCount 그날 예정된 루틴 수(개인 루틴 + 그룹 루틴) — 분모
     * @param completedCount 그날 완료한 루틴 수(개인 루틴 + 그룹 루틴) — 분자
     */
    @Builder
    public record DailyMark(
            LocalDate date,
            int scheduledCount,
            int completedCount,
            boolean isToday
    ) {
    }

    /**
     * 하단 "활동 통계" 카드. 항상 이번 달(오늘이 속한 달, KST) 기준으로 집계한다 —
     * 주간/월간 뷰를 오가도 해당 달의 활동 통계만 보여지며, 달을 바꿀 경우 해당 달의 리포트를 보여준다.
     *
     * @param completedRoutineCount  이번 달 완료한 루틴 수. 개인 루틴 인증 + 그룹 루틴
     *                               완료 건수를 합친다.
     * @param averageCompletionRate 이번 달 평균 달성률(%). 하루하루의
     *                               {@code completedCount / scheduledCount}를 구한 뒤
     *                               그 값이 0보다 큰 날만 모아 평균한다 — 예정된 루틴이
     *                               없거나 하나도 완료하지 못한 날은 평균에서 뺀다.
     * @param completedChallengeCount 디자인상 챌린지에 "완료" 개념이 없어, 우선 이번 달
     *                                기준으로 참여 중인 챌린지 개수를 대신 보여준다.
     * @param earnedCoinCount 상점·코인 기능이 아직 없어 항상 0이다.
     */
    @Builder
    public record ActivityStats(
            int completedRoutineCount,
            int averageCompletionRate,
            int completedChallengeCount,
            int earnedCoinCount
    ) {
    }
}
