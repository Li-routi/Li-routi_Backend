package com.lirouti.domain.challenge.dto.response;

import java.util.List;

import com.lirouti.domain.challenge.enums.ChallengeCategory;

import lombok.Builder;

public final class ChallengeResDTO {
    private ChallengeResDTO() {
    }

    // 목록 응답 래퍼. 지금은 totalCount == challenges.size()지만, 향후 페이지네이션·총건수를
    // 추가해도 응답 스키마(result가 객체)가 유지되도록 처음부터 래핑한다.
    @Builder
    public record Listing(
            List<Summary> challenges,
            int totalCount
    ) {
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
