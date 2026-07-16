package com.lirouti.domain.challenge.dto.response;

import com.lirouti.domain.challenge.enums.ChallengeCategory;

import lombok.Builder;

public final class ChallengeResDTO {
    private ChallengeResDTO() {
    }

    // 목록 카드 한 건
    @Builder
    public record Summary(
            Long challengeId,
            String name,
            String description,
            ChallengeCategory category,
            long participantCount
    ) {
    }

    // 상세 화면
    @Builder
    public record Detail(
            Long challengeId,
            String name,
            String description,
            ChallengeCategory category,
            long participantCount,
            long todayCompletionCount
    ) {
    }
}
