package com.lirouti.domain.report.dto.projection;

import java.time.LocalDate;

/**
 * 리포트 집계용. 특정 날짜의 (예정 건수, 완료 건수) 쌍.
 *
 * <p>개인 루틴은 {@code MemberRoutineVerificationRepository.findDailyCompletionCounts}가
 * 완료 건수만 주고 예정 건수는 서비스가 스케줄에서 계산하는 반면, 그룹 루틴은
 * {@code GroupRoutineAssignment}가 애초에 "그날 할당된 건"을 저장하므로 예정·완료를
 * 한 쿼리에서 함께 뽑을 수 있다.
 */
public record DailyScheduleAndCompletion(
        LocalDate date,
        long scheduledCount,
        long completedCount
) {
}
