package com.lirouti.domain.challenge.converter;

import java.util.List;

import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.repository.ChallengeSummaryProjection;

public final class ChallengeConverter {
    private ChallengeConverter() {
    }

    public static ChallengeResDTO.Summary toSummary(ChallengeSummaryProjection projection) {
        Challenge challenge = projection.challenge();
        return ChallengeResDTO.Summary.builder()
                .challengeId(challenge.getId())
                .name(challenge.getName())
                .description(challenge.getDescription())
                .category(challenge.getCategory())
                .participantCount(projection.participantCount())
                .build();
    }

    public static ChallengeResDTO.Listing toListing(List<ChallengeSummaryProjection> projections) {
        List<ChallengeResDTO.Summary> summaries = projections.stream()
                .map(ChallengeConverter::toSummary)
                .toList();
        return ChallengeResDTO.Listing.builder()
                .challenges(summaries)
                .totalCount(summaries.size())
                .build();
    }

    // 참여자 수·오늘 완료자 수는 Service가 집계해 매개변수로 넘긴다.
    public static ChallengeResDTO.Detail toDetail(
            Challenge challenge,
            long participantCount,
            long todayCompletionCount
    ) {
        return ChallengeResDTO.Detail.builder()
                .challengeId(challenge.getId())
                .name(challenge.getName())
                .description(challenge.getDescription())
                .category(challenge.getCategory())
                .participantCount(participantCount)
                .todayCompletionCount(todayCompletionCount)
                .build();
    }
}
