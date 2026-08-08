package com.lirouti.domain.verification.dto.response;

import com.lirouti.domain.verification.enums.ReviewStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 챌린지 인증의 응답 묶음.
 *
 * <p>루틴 인증의 {@link VerificationResDTO}와 이름을 나눈 이유는 대상이 다르기 때문이다.
 * 챌린지 인증은 사진이 필수이고 공개 피드에 나가지만, 그룹·개인 루틴 인증은 그렇지 않다.
 * 응답 모양이 서로 달라 한 클래스에 담으면 어느 필드가 어느 대상의 것인지 흐려진다.
 */
public final class ChallengeVerificationResDTO {
    private ChallengeVerificationResDTO() {
    }

    /**
     * 인증하기 결과.
     * reverified가 true면 오늘 이미 인증한 건을 덮어쓴 것(사진 교체)이라 스트릭이 오르지 않는다.
     * imageUrl은 저장된 key가 아니라 조립된 공개 URL이다.
     */
    @Builder
    public record Verification(
            Long verificationId,
            Long challengeId,
            LocalDate verifiedDate,
            LocalDateTime verifiedAt,
            String imageUrl,
            String content,
            int currentStreak,
            boolean reverified
    ) {
    }

    // 최신 인증 피드 래퍼. 챌린지 목록과 같은 커서 방식이되 커서 값은 verificationId다.
    @Builder
    public record Feed(
            List<FeedItem> verifications,
            Long nextCursor,
            boolean hasNext
    ) {
    }

    /**
     * 인증 메모 수정 결과.
     *
     * <p>바뀐 값만 돌려준다. 클라이언트가 재조회 없이 카드의 메모를 갈아끼우게 하기 위한 것이라
     * 사진·스트릭처럼 안 바뀐 값은 싣지 않는다.
     *
     * <p>{@code content} 는 비웠으면 {@code null} 이다.
     */
    @Builder
    public record MemoUpdate(
            Long verificationId,
            String content
    ) {
    }

    /**
     * 인증 신고 결과.
     * 신고해도 인증은 삭제되지 않는다 — 신고자 본인의 이후 조회에서 빠지고,
     * 신고가 임계값만큼 쌓이면 전체 회원에게 가려진다.
     */
    @Schema(name = "ChallengeVerificationReportResult", description = "챌린지 인증 신고 결과")
    @Builder
    public record Report(
            Long reportId,
            Long verificationId
    ) {
    }

    /**
     * 피드 카드 한 건. 닉네임·사진·코멘트에 좋아요 수와 내가 눌렀는지를 함께 보여준다.
     *
     * <p>{@code mine}은 <b>이 인증을 조회자 본인이 올렸는지</b>다. 서버가 판정해 내려주는 이유는
     * 클라이언트가 판정할 방법이 없기 때문이다 — 응답에 작성자 식별자가 없고, 닉네임은
     * 유니크 제약이 없어(회원 유니크는 이메일과 소셜 식별자뿐) 동명이인이 생기면 남의 글이
     * 내 글로 보인다. 그 값으로 삭제·수정 버튼을 그리면 그대로 사고다.
     *
     * <p>{@code liked}와 같은 방식이다 — 둘 다 JWT에서 얻은 조회자를 기준으로 서버가 정한다.
     */
    @Builder
    public record FeedItem(
            Long verificationId,
            String nickname,
            String imageUrl,
            String content,
            LocalDateTime verifiedAt,
            long likeCount,
            boolean liked,
            boolean mine
    ) {
    }

    /**
     * 좋아요·취소 결과.
     *
     * 최종 상태를 실어 클라이언트가 재조회 없이 화면을 갱신하게 한다. 좋아요·취소는 멱등하므로
     * 이미 눌린 상태에서 다시 눌러도 오류가 아니라 같은 응답이 나간다.
     */
    @Builder
    public record Like(
            Long verificationId,
            long likeCount,
            boolean liked
    ) {
    }

    /**
     * 내 인증 목록 래퍼. 커서 방식은 피드와 같고 커서 값도 verificationId다.
     *
     * currentStreak을 함께 싣는 이유는, 이 화면이 "며칠째 이어오고 있는지"와 목록을 같이 보여주기
     * 때문이다. 챌린지 상세에도 있지만 그쪽은 챌린지 정보를 받는 호출이라 스크롤 도중에는
     * 다시 부르지 않는다.
     *
     * <p>{@code currentParticipationRound}는 <b>지금 참여의 회차</b>다. 항목의
     * {@code participationRound}와 비교해 "이번 참여 / 지난 참여"를 가른다 — 목록이 전체 회차를
     * 담으므로, 이 값이 없으면 클라이언트가 어디까지가 이번 참여인지 알 수 없다.
     *
     * <p><b>스트릭과 목록의 기준이 다르다.</b> 스트릭은 현재 회차만 보고 목록은 전부 담으므로,
     * "0일 연속" 옆에 지난 참여 기록이 놓일 수 있다. 회차를 실은 이유가 그것이다 —
     * 클라이언트가 구분해 보여줄 수 있다.
     */
    @Builder
    public record MyVerifications(
            List<MyVerificationItem> verifications,
            int currentStreak,
            int currentParticipationRound,
            Long nextCursor,
            boolean hasNext
    ) {
    }

    /**
     * 내 인증 한 건.
     *
     * 피드(FeedItem)와 달리 nickname이 없다 — 전부 본인이라 화면에 쓸 데가 없다.
     * 대신 verifiedDate(KST 기준일)를 싣는다. 날짜별로 묶어 보여주려면 시각이 아니라 기준일이
     * 필요한데, 당일 재인증은 verifiedAt만 덮어쓰고 verifiedDate는 그대로여서 둘이 다를 수 있다.
     *
     * likeCount는 있고 liked는 없다. 좋아요는 인증 한 건에 붙으므로 같은 사진이 피드에
     * 나올 때와 수가 같아야 한다 — 한쪽만 비어 있으면 화면이 어긋난다. 반면 "내가 눌렀는지"는
     * 자기 게시물에서 쓸 데가 없다.
     *
     * <p>{@code participationRound}는 그 인증을 남긴 참여 회차다. 래퍼의
     * {@code currentParticipationRound}보다 작으면 <b>지난 참여</b>의 기록이다 —
     * 나갔다가 다시 들어오면 회차가 오르고, 그 이전 인증이 여기 해당한다.
     */
    @Builder
    public record MyVerificationItem(
            Long verificationId,
            String imageUrl,
            String content,
            LocalDate verifiedDate,
            LocalDateTime verifiedAt,
            long likeCount,
            int participationRound,
            /**
             * 심사 상태. {@code PENDING} 이면 아직 공개되지 않았고 <b>본인에게만</b> 보인다 —
             * 그 경우 {@code imageUrl} 은 공개 주소가 아니라 한시적 서명 주소다.
             */
            ReviewStatus reviewStatus
    ) {
    }
}
