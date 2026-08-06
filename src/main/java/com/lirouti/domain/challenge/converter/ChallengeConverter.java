package com.lirouti.domain.challenge.converter;

import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.verification.dto.response.ChallengeVerificationResDTO;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.verification.entity.ChallengeVerificationReport;
import com.lirouti.domain.challenge.entity.MemberChallenge;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChallengeConverter {
    private ChallengeConverter() {
    }

    // 참여/이탈 결과. challenge 프록시의 id만 읽으므로 추가 조회가 없다.
    public static ChallengeResDTO.Participation toParticipation(MemberChallenge memberChallenge) {
        return ChallengeResDTO.Participation.builder()
                .challengeId(memberChallenge.getChallenge().getId())
                .participating(memberChallenge.isParticipating())
                .participationRound(memberChallenge.getParticipationRound())
                .build();
    }

    // 전체 목록 카드. 참여자 수·인증 게시글 수는 Service가 배치 집계한 맵에서 꺼내 채운다.
    public static ChallengeResDTO.Summary toSummary(
            Challenge challenge,
            long participantCount,
            long verificationPostCount
    ) {
        return ChallengeResDTO.Summary.builder()
                .challengeId(challenge.getId())
                .name(challenge.getName())
                .description(challenge.getDescription())
                .imageUrl(challenge.getImageUrl())
                .category(challenge.getCategory())
                .routineCycle(challenge.getRoutineCycle())
                .reward(challenge.getReward())
                .participantCount(participantCount)
                .verificationPostCount(verificationPostCount)
                .build();
    }

    // 전체 목록 커서 응답. 집계 없는 챌린지는 맵에 없으므로 0으로 채운다.
    public static ChallengeResDTO.Listing toListing(
            List<Challenge> challenges,
            Map<Long, Long> participantCounts,
            Map<Long, Long> verificationCounts,
            Long nextCursor,
            boolean hasNext
    ) {
        List<ChallengeResDTO.Summary> summaries = challenges.stream()
                .map(c -> toSummary(
                        c,
                        participantCounts.getOrDefault(c.getId(), 0L),
                        verificationCounts.getOrDefault(c.getId(), 0L)))
                .toList();
        return ChallengeResDTO.Listing.builder()
                .challenges(summaries)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    // 내 챌린지 목록 카드(심플). 전부 참여 중이라 통계를 담지 않는다.
    public static ChallengeResDTO.MySummary toMySummary(Challenge challenge) {
        return ChallengeResDTO.MySummary.builder()
                .challengeId(challenge.getId())
                .name(challenge.getName())
                .description(challenge.getDescription())
                .imageUrl(challenge.getImageUrl())
                .category(challenge.getCategory())
                .build();
    }

    public static ChallengeResDTO.MyListing toMyListing(List<Challenge> challenges) {
        List<ChallengeResDTO.MySummary> summaries = challenges.stream()
                .map(ChallengeConverter::toMySummary)
                .toList();
        return ChallengeResDTO.MyListing.builder()
                .challenges(summaries)
                .build();
    }

    // 참여 여부·주기 인증 여부·집계 수치는 Service가 조회해 매개변수로 넘긴다.
    public static ChallengeResDTO.Detail toDetail(
            Challenge challenge,
            boolean participating,
            boolean verifiedInCurrentPeriod,
            long participantCount,
            long verificationPostCount,
            long todayCompletionCount
    ) {
        return ChallengeResDTO.Detail.builder()
                .challengeId(challenge.getId())
                .name(challenge.getName())
                .description(challenge.getDescription())
                .imageUrl(challenge.getImageUrl())
                .category(challenge.getCategory())
                .routineCycle(challenge.getRoutineCycle())
                .reward(challenge.getReward())
                .participating(participating)
                .verifiedInCurrentPeriod(verifiedInCurrentPeriod)
                .participantCount(participantCount)
                .verificationPostCount(verificationPostCount)
                .todayCompletionCount(todayCompletionCount)
                .build();
    }
}
