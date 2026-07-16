package com.lirouti.domain.challenge.repository;

import com.lirouti.domain.challenge.entity.Challenge;

/**
 * 목록 조회 결과 — 챌린지 하나와 그 참여자 수. QueryDSL 프로젝션용 내부 홀더다.
 * 응답 DTO 조립은 Converter가 담당한다.
 */
public record ChallengeSummaryProjection(
        Challenge challenge,
        long participantCount
) {
}
