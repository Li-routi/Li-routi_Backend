package com.lirouti.domain.challenge.converter;

import java.util.List;

import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;

public final class ChallengeConverter {
    private ChallengeConverter() {
    }

    public static ChallengeResDTO.Summary toSummary(Challenge challenge) {
        return ChallengeResDTO.Summary.builder()
                .challengeId(challenge.getId())
                .name(challenge.getName())
                .description(challenge.getDescription())
                .imageUrl(challenge.getImageUrl())
                .category(challenge.getCategory())
                .build();
    }

    // 무한 스크롤 커서 응답. nextCursor·hasNext는 Service가 계산해 넘긴다.
    public static ChallengeResDTO.Listing toListing(
            List<Challenge> challenges,
            Long nextCursor,
            boolean hasNext
    ) {
        List<ChallengeResDTO.Summary> summaries = challenges.stream()
                .map(ChallengeConverter::toSummary)
                .toList();
        return ChallengeResDTO.Listing.builder()
                .challenges(summaries)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
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
                .imageUrl(challenge.getImageUrl())
                .category(challenge.getCategory())
                .participantCount(participantCount)
                .todayCompletionCount(todayCompletionCount)
                .build();
    }
}
