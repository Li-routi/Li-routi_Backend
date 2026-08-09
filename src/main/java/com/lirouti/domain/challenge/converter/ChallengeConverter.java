package com.lirouti.domain.challenge.converter;

import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.MemberChallenge;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChallengeConverter {

    /**
     * 대표 이미지. <b>운영이 지정한 표지가 있으면 그것이 이긴다.</b>
     *
     * <p>{@code challenge.image_url} 은 원래 운영 표지 자리로 만든 컬럼이고 그 계획이 사라진
     * 것은 아니다. 지금은 전부 비어 있어 인기 인증 사진이 보이지만, 나중에 운영이 표지를
     * 넣으면 그쪽이 우선한다 — 둘 중 하나를 버리지 않아도 된다.
     *
     * <p>인증이 하나도 없는 챌린지는 {@code null} 이다. 클라이언트가 기본 아이콘을 그린다.
     */
    private static String coverImageOf(Challenge challenge, String coverImageUrl) {
        String operatorCover = challenge.getImageUrl();
        return (operatorCover != null && !operatorCover.isBlank()) ? operatorCover : coverImageUrl;
    }

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
            long verificationPostCount,
            String coverImageUrl
    ) {
        return ChallengeResDTO.Summary.builder()
                .challengeId(challenge.getId())
                .name(challenge.getName())
                .description(challenge.getDescription())
                .imageUrl(coverImageOf(challenge, coverImageUrl))
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
            Map<Long, String> coverImages,
            Long nextCursor,
            boolean hasNext
    ) {
        List<ChallengeResDTO.Summary> summaries = challenges.stream()
                .map(c -> toSummary(
                        c,
                        participantCounts.getOrDefault(c.getId(), 0L),
                        verificationCounts.getOrDefault(c.getId(), 0L),
                        coverImages.get(c.getId())))
                .toList();
        return ChallengeResDTO.Listing.builder()
                .challenges(summaries)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    // 내 챌린지 목록 카드(심플). 전부 참여 중이라 통계를 담지 않는다.
    public static ChallengeResDTO.MySummary toMySummary(Challenge challenge, String coverImageUrl) {
        return ChallengeResDTO.MySummary.builder()
                .challengeId(challenge.getId())
                .name(challenge.getName())
                .description(challenge.getDescription())
                .imageUrl(coverImageOf(challenge, coverImageUrl))
                .category(challenge.getCategory())
                .build();
    }

    // 내 챌린지 카드도 같은 표지를 쓴다. 여기만 비워 두면 같은 챌린지가 찾아보기에서는
    // 사진이 있고 내 목록에서는 없는 상태가 된다.
    public static ChallengeResDTO.MyListing toMyListing(
            List<Challenge> challenges,
            Map<Long, String> coverImages
    ) {
        List<ChallengeResDTO.MySummary> summaries = challenges.stream()
                .map(c -> toMySummary(c, coverImages.get(c.getId())))
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
            String coverImageUrl
    ) {
        return ChallengeResDTO.Detail.builder()
                .challengeId(challenge.getId())
                .name(challenge.getName())
                .description(challenge.getDescription())
                .imageUrl(coverImageOf(challenge, coverImageUrl))
                .category(challenge.getCategory())
                .routineCycle(challenge.getRoutineCycle())
                .reward(challenge.getReward())
                .participating(participating)
                .verifiedInCurrentPeriod(verifiedInCurrentPeriod)
                .participantCount(participantCount)
                .verificationPostCount(verificationPostCount)
                .build();
    }
}
