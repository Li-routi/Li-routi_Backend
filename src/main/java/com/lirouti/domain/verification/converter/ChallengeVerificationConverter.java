package com.lirouti.domain.verification.converter;

import com.lirouti.domain.verification.dto.response.ChallengeVerificationResDTO;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.verification.entity.ChallengeVerificationReport;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 챌린지 인증 엔티티를 응답 DTO로 옮긴다.
 *
 * <p>루틴 인증의 {@link VerificationConverter}와 나눈 이유는 다루는 엔티티가 다르기 때문이다.
 * 한 클래스에 두면 어느 메서드가 어느 대상의 것인지 이름으로만 구분해야 한다.
 */
public final class ChallengeVerificationConverter {
    private ChallengeVerificationConverter() {
    }

    /**
     * 인증하기 결과. imageUrl은 저장된 key가 아니라 Service가 조립한 공개 URL을 받는다.
     * challenge 프록시의 id만 읽으므로 추가 조회가 없다.
     */
    /** 메모 수정 결과. 바뀐 값만 담아 클라이언트가 재조회 없이 카드를 갱신하게 한다. */
    public static ChallengeVerificationResDTO.MemoUpdate toMemoUpdate(ChallengeVerification verification) {
        return ChallengeVerificationResDTO.MemoUpdate.builder()
                .verificationId(verification.getId())
                .content(verification.getContent())
                .build();
    }

    public static ChallengeVerificationResDTO.Verification toVerification(
            ChallengeVerification verification,
            String imageUrl,
            int currentStreak,
            boolean reverified
    ) {
        return ChallengeVerificationResDTO.Verification.builder()
                .verificationId(verification.getId())
                .challengeId(verification.getMemberChallenge().getChallenge().getId())
                .verifiedDate(verification.getVerifiedDate())
                .verifiedAt(verification.getVerifiedAt())
                .imageUrl(imageUrl)
                .content(verification.getContent())
                .currentStreak(currentStreak)
                .reverified(reverified)
                .reviewStatus(verification.getReviewStatus())
                .build();
    }

    // 인증 신고 결과. 신고 id와 대상 인증 id만 돌려준다(인증 내용은 신고 응답에 필요 없다).
    public static ChallengeVerificationResDTO.Report toReport(ChallengeVerificationReport report) {
        return ChallengeVerificationResDTO.Report.builder()
                .reportId(report.getId())
                .verificationId(report.getChallengeVerification().getId())
                .build();
    }

    // 피드 카드 한 건. 공개 URL·좋아요 수·좋아요 여부는 Service가 배치로 구해 넘긴다.
    public static ChallengeVerificationResDTO.FeedItem toFeedItem(
            ChallengeVerification verification,
            String imageUrl,
            long likeCount,
            boolean liked,
            boolean mine
    ) {
        return ChallengeVerificationResDTO.FeedItem.builder()
                .verificationId(verification.getId())
                .nickname(verification.getMemberChallenge().getMember().getNickname())
                .imageUrl(imageUrl)
                .content(verification.getContent())
                .verifiedAt(verification.getVerifiedAt())
                .likeCount(likeCount)
                .liked(liked)
                .mine(mine)
                .build();
    }

    /**
     * 이 인증을 조회자 본인이 올렸는지.
     *
     * <p>추가 조회가 없다. 피드 쿼리가 참여와 회원을 fetch join 으로 이미 읽어 두었기 때문이다
     * (그 join 을 빼면 이 줄이 페이지 크기만큼 회원 조회를 일으킨다).
     *
     * <p>{@code viewerId} 가 없으면 false 다. 모든 챌린지 경로가 로그인을 요구하므로 실제로는
     * null 이 오지 않지만, 온다면 "누구의 것도 아니다"가 맞다 — 익명에게 남의 글을 자기 것으로
     * 보여 주는 쪽이 훨씬 나쁘다.
     */
    private static boolean isMine(ChallengeVerification verification, Long viewerId) {
        if (viewerId == null) {
            return false;
        }
        return viewerId.equals(verification.getMemberChallenge().getMember().getId());
    }

    // 좋아요·취소 결과(#63). 최종 상태만 담아 클라이언트가 재조회 없이 갱신하게 한다.
    public static ChallengeVerificationResDTO.Like toLike(Long verificationId, long likeCount, boolean liked) {
        return ChallengeVerificationResDTO.Like.builder()
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
    public static ChallengeVerificationResDTO.Feed toFeed(
            List<ChallengeVerification> verifications,
            Map<Long, String> imageUrls,
            Map<Long, Long> likeCounts,
            Set<Long> likedIds,
            Long viewerId,
            Long nextCursor,
            Long nextCursorLikeCount,
            boolean hasNext
    ) {
        List<ChallengeVerificationResDTO.FeedItem> items = verifications.stream()
                .map(v -> toFeedItem(
                        v,
                        imageUrls.get(v.getId()),
                        likeCounts.getOrDefault(v.getId(), 0L),
                        likedIds.contains(v.getId()),
                        isMine(v, viewerId)))
                .toList();
        return ChallengeVerificationResDTO.Feed.builder()
                .verifications(items)
                .nextCursor(nextCursor)
                .nextCursorLikeCount(nextCursorLikeCount)
                .hasNext(hasNext)
                .build();
    }

    // 내 인증 한 건(#62). 닉네임은 싣지 않는다 — 전부 본인이다.
    public static ChallengeVerificationResDTO.MyVerificationItem toMyVerificationItem(
            ChallengeVerification verification,
            String imageUrl,
            long likeCount
    ) {
        return ChallengeVerificationResDTO.MyVerificationItem.builder()
                .verificationId(verification.getId())
                .imageUrl(imageUrl)
                .content(verification.getContent())
                .verifiedDate(verification.getVerifiedDate())
                .verifiedAt(verification.getVerifiedAt())
                .likeCount(likeCount)
                // 인증 행이 스냅샷으로 들고 있는 값이라 추가 조회가 없다.
                .participationRound(verification.getParticipationRound())
                .reviewStatus(verification.getReviewStatus())
                .build();
    }

    /**
     * 내 인증 목록 커서 응답(#62).
     * imageUrls·currentStreak은 Service가 계산해 넘긴다(Converter는 전달받은 값만 매핑한다).
     */
    public static ChallengeVerificationResDTO.MyVerifications toMyVerifications(
            List<ChallengeVerification> verifications,
            Map<Long, String> imageUrls,
            Map<Long, Long> likeCounts,
            int currentStreak,
            int currentParticipationRound,
            Long nextCursor,
            Long nextCursorLikeCount,
            boolean hasNext
    ) {
        List<ChallengeVerificationResDTO.MyVerificationItem> items = verifications.stream()
                .map(v -> toMyVerificationItem(
                        v, imageUrls.get(v.getId()), likeCounts.getOrDefault(v.getId(), 0L)))
                .toList();
        return ChallengeVerificationResDTO.MyVerifications.builder()
                .verifications(items)
                .currentStreak(currentStreak)
                .currentParticipationRound(currentParticipationRound)
                .nextCursor(nextCursor)
                .nextCursorLikeCount(nextCursorLikeCount)
                .hasNext(hasNext)
                .build();
    }

    public static ChallengeVerificationResDTO.Deletion toDeletion(Long verificationId) {
        return ChallengeVerificationResDTO.Deletion.builder()
                .verificationId(verificationId)
                .build();
    }
}
