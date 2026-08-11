package com.lirouti.domain.report.dto.response;

import lombok.Builder;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * 마이 > 리포트 화면 응답.
 *
 * <p>주간(막대 그래프)·월간(달력) 두 뷰가 있지만 하단의 {@link ActivityStats}("활동 통계")는
 * 뷰와 무관하게 항상 "현재 조회 중인 달" 기준으로 집계된다.
 * 월간 뷰에서는 요청받은 {@code yearMonth}가, 주간 뷰에서는 해당 주가 표시되는 {@code displayMonth}가 그 기준이 된다.
 * 사용자가 이전/다음 달로 이동하면 그 달의 리포트와 통계가 함께 갱신된다.
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
            LocalDate weekEnd,
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
     * 월간 리포트, 달력 형태로 하루마다 달성률을 표시한다.
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
     * 하단 "활동 통계" 카드. "현재 조회 중인 달" 기준으로 집계한다 — 월간 뷰에서는
     * 요청받은 달, 주간 뷰에서는 해당 주가 속한 표시 월({@code displayMonth}) 기준이며,
     * 사용자가 이전/다음 달로 이동하면 그 달 기준으로 다시 집계된다.
     *
     * @param completedRoutineCount  대상 월에 완료한 루틴 수. 개인 루틴 인증 + 그룹 루틴
     *                               완료 건수를 합친다.
     * @param averageCompletionRate 대상 월 평균 달성률(%). 하루하루의
     *                               {@code completedCount / scheduledCount}를 구한 뒤
     *                               그 값이 0보다 큰 날만 모아 평균한다 — 예정된 루틴이
     *                               없거나 하나도 완료하지 못한 날은 평균에서 뺀다.
     * @param completedChallengeCount 대상 월에 챌린지를 제출해 <b>승인</b>된 횟수. 챌린지는
     * *                                하루 1회만 제출 가능하고 승인될 때마다 +1되며, 다음 날이
     * *                                되면 같은 챌린지에 다시 제출할 수 있다. 심사 보류(PENDING)
     * *                                상태는 아직 세지 않는다.
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
