package com.lirouti.domain.challenge.converter;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lirouti.domain.challenge.dto.response.ChallengeResDTO;
import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.ChallengeVerificationReport;
import com.lirouti.domain.challenge.entity.MemberChallenge;

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

    /**
     * 인증하기 결과. imageUrl은 저장된 key가 아니라 Service가 조립한 공개 URL을 받는다.
     * challenge 프록시의 id만 읽으므로 추가 조회가 없다.
     */
    public static ChallengeResDTO.Verification toVerification(
            ChallengeVerification verification,
            String imageUrl,
            int currentStreak,
            boolean reverified
    ) {
        return ChallengeResDTO.Verification.builder()
                .verificationId(verification.getId())
                .challengeId(verification.getMemberChallenge().getChallenge().getId())
                .verifiedDate(verification.getVerifiedDate())
                .verifiedAt(verification.getVerifiedAt())
                .imageUrl(imageUrl)
                .content(verification.getContent())
                .currentStreak(currentStreak)
                .reverified(reverified)
                .build();
    }

    // 인증 신고 결과. 신고 id와 대상 인증 id만 돌려준다(인증 내용은 신고 응답에 필요 없다).
    public static ChallengeResDTO.Report toReport(ChallengeVerificationReport report) {
        return ChallengeResDTO.Report.builder()
                .reportId(report.getId())
                .verificationId(report.getChallengeVerification().getId())
                .build();
    }

    // 피드 카드 한 건. 공개 URL·좋아요 수·좋아요 여부는 Service가 배치로 구해 넘긴다.
    public static ChallengeResDTO.FeedItem toFeedItem(
            ChallengeVerification verification,
            String imageUrl,
            long likeCount,
            boolean liked
    ) {
        return ChallengeResDTO.FeedItem.builder()
                .verificationId(verification.getId())
                .nickname(verification.getMemberChallenge().getMember().getNickname())
                .imageUrl(imageUrl)
                .content(verification.getContent())
                .verifiedAt(verification.getVerifiedAt())
                .likeCount(likeCount)
                .liked(liked)
                .build();
    }

    // 좋아요·취소 결과(#63). 최종 상태만 담아 클라이언트가 재조회 없이 갱신하게 한다.
    public static ChallengeResDTO.Like toLike(Long verificationId, long likeCount, boolean liked) {
        return ChallengeResDTO.Like.builder()
                .verificationId(verificationId)
                .likeCount(likeCount)
                .liked(liked)
                .build();
    }

    /**
     * 인증 피드 커서 응답.
     * imageUrls는 Service가 인증 id별로 미리 조립해 둔 공개 URL 맵이다
     * (Converter는 외부 규칙에 의존하지 않고 전달받은 값만 매핑한다).
     */
    public static ChallengeResDTO.Feed toFeed(
            List<ChallengeVerification> verifications,
            Map<Long, String> imageUrls,
            Map<Long, Long> likeCounts,
            Set<Long> likedIds,
            Long nextCursor,
            boolean hasNext
    ) {
        List<ChallengeResDTO.FeedItem> items = verifications.stream()
                .map(v -> toFeedItem(
                        v,
                        imageUrls.get(v.getId()),
                        likeCounts.getOrDefault(v.getId(), 0L),
                        likedIds.contains(v.getId())))
                .toList();
        return ChallengeResDTO.Feed.builder()
                .verifications(items)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    // 내 인증 한 건(#62). 닉네임은 싣지 않는다 — 전부 본인이다.
    public static ChallengeResDTO.MyVerificationItem toMyVerificationItem(
            ChallengeVerification verification,
            String imageUrl,
            long likeCount
    ) {
        return ChallengeResDTO.MyVerificationItem.builder()
                .verificationId(verification.getId())
                .imageUrl(imageUrl)
                .content(verification.getContent())
                .verifiedDate(verification.getVerifiedDate())
                .verifiedAt(verification.getVerifiedAt())
                .likeCount(likeCount)
                .build();
    }

    /**
     * 내 인증 목록 커서 응답(#62).
     * imageUrls·currentStreak은 Service가 계산해 넘긴다(Converter는 전달받은 값만 매핑한다).
     */
    public static ChallengeResDTO.MyVerifications toMyVerifications(
            List<ChallengeVerification> verifications,
            Map<Long, String> imageUrls,
            Map<Long, Long> likeCounts,
            int currentStreak,
            Long nextCursor,
            boolean hasNext
    ) {
        List<ChallengeResDTO.MyVerificationItem> items = verifications.stream()
                .map(v -> toMyVerificationItem(
                        v, imageUrls.get(v.getId()), likeCounts.getOrDefault(v.getId(), 0L)))
                .toList();
        return ChallengeResDTO.MyVerifications.builder()
                .verifications(items)
                .currentStreak(currentStreak)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    // 참여 여부·집계 수치는 Service가 조회해 매개변수로 넘긴다.
    public static ChallengeResDTO.Detail toDetail(
            Challenge challenge,
            boolean participating,
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
                .participantCount(participantCount)
                .verificationPostCount(verificationPostCount)
                .todayCompletionCount(todayCompletionCount)
                .build();
    }
}
