package com.lirouti.domain.verification.dto.projection;

import java.time.LocalDate;

/**
 * 리포트 집계용 프로젝션 : 날짜 별 그룹 루틴 할당 수 및 완료 수
 */
public record DailyAssignmentStat(LocalDate assignedDate, Long total, Long count) {
}
