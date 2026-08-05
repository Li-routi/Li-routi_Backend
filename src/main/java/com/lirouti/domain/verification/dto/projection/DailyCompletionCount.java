package com.lirouti.domain.verification.dto.projection;

import java.time.LocalDate;

/**
 * 리포트 집계용 프로젝션 : 개인 루틴 인증 건수 확인
 */
public record DailyCompletionCount(LocalDate verifiedDate, Long count) {
}
