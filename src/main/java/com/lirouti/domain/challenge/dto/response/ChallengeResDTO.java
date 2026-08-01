package com.lirouti.domain.challenge.dto.response;

import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.enums.RoutineCycle;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ChallengeResDTO {
    private ChallengeResDTO() {
    }

    // 목록 응답 래퍼. 무한 스크롤용 커서 페이지네이션(최신순).
    // 클라이언트는 첫 요청에 cursor 없이 보내고, 응답의 nextCursor를 다음 요청의 cursor로 넘긴다.
    // hasNext가 false(= nextCursor가 null)면 더 이상 요청하지 않는다.
    @Builder
    public record Listing(
            List<Summary> challenges,
            Long nextCursor,
            boolean hasNext
    ) {
    }

    // 전체(찾아보기) 목록 카드 한 건.
    // category·routineCycle은 enum으로 내려주고 프론트가 한글(건강·매일 등)로 변환한다.
    // reward는 챌린지 달성 시 부여되는 재화 수량이다. 컬럼이 NOT NULL DEFAULT 0이고
    // 엔티티 빌더도 null이면 0으로 채우므로 null이 내려가지 않는다(그래서 int).
    @Builder
    public record Summary(
            Long challengeId,
            String name,
            String description,
            String imageUrl,
            ChallengeCategory category,
            RoutineCycle routineCycle,
            int reward,
            long participantCount,
            long verificationPostCount
    ) {
    }

    // 내 챌린지 목록 래퍼. 참여 중인 것만 모아 보여주는 화면이라 페이지네이션이 없다.
    @Builder
    public record MyListing(
            List<MySummary> challenges
    ) {
    }

    // 내 챌린지 목록 카드 한 건. 전부 참여 중이므로 통계 없이 심플하게 낸다.
    @Builder
    public record MySummary(
            Long challengeId,
            String name,
            String description,
            String imageUrl,
            ChallengeCategory category
    ) {
    }

    // 참여/이탈 결과. participating=true면 참여 중, false면 이탈. 재참여 시 회차가 올라간다.
    @Builder
    public record Participation(
            Long challengeId,
            boolean participating,
            int participationRound
    ) {
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

    // 최신 인증 피드 래퍼. 목록과 같은 커서 방식이되 커서 값은 verificationId다.
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
     * 좋아요·취소 결과(#63).
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
     * 내 인증 목록 래퍼(#62). 커서 방식은 피드와 같고 커서 값도 verificationId다.
     *
     * currentStreak을 함께 싣는 이유는, 이 화면이 "며칠째 이어오고 있는지"와 목록을 같이 보여주기
     * 때문이다. 상세(Detail)에도 있지만 그쪽은 챌린지 정보를 받는 호출이라 스크롤 도중에는
     * 다시 부르지 않는다.
     */
    @Builder
    public record MyVerifications(
            List<MyVerificationItem> verifications,
            int currentStreak,
            Long nextCursor,
            boolean hasNext
    ) {
    }

    /**
     * 내 인증 한 건(#62).
     *
     * 피드(FeedItem)와 달리 nickname이 없다 — 전부 본인이라 화면에 쓸 데가 없다.
     * 대신 verifiedDate(KST 기준일)를 싣는다. 날짜별로 묶어 보여주려면 시각이 아니라 기준일이
     * 필요한데, 당일 재인증은 verifiedAt만 덮어쓰고 verifiedDate는 그대로여서 둘이 다를 수 있다.
     *
     * likeCount는 있고 liked는 없다(#63). 좋아요는 인증 한 건에 붙으므로 같은 사진이 피드에
     * 나올 때와 수가 같아야 한다 — 한쪽만 비어 있으면 화면이 어긋난다. 반면 "내가 눌렀는지"는
     * 자기 게시물에서 쓸 데가 없다.
     */
    @Builder
    public record MyVerificationItem(
            Long verificationId,
            String imageUrl,
            String content,
            LocalDate verifiedDate,
            LocalDateTime verifiedAt,
            long likeCount
    ) {
    }

    /**
     * 상세 화면.
     *
     * <p>{@code participating}: 조회자가 현재 참여 중인지. {@code reward}: 달성 시 부여되는 재화
     * 수량(목록 카드와 같은 값). {@code verificationPostCount}: 인증 게시글 수(상단 통계).
     *
     * <h3>인증하기 버튼은 두 값으로 그린다</h3>
     * <pre>
     * participating = false                              → "참여하기"
     * participating = true,  verifiedInCurrentPeriod = false → "인증하기"
     * participating = true,  verifiedInCurrentPeriod = true  → "완료" (비활성)
     * </pre>
     *
     * <p>{@code verifiedInCurrentPeriod} 는 <b>"오늘 인증했는지"가 아니라 "현재 주기 구간에
     * 인증했는지"</b>다. 이름을 그렇게 정한 이유는 확장 때문이다 — 지금 챌린지는 전부
     * {@code DAILY} 라 결과가 "오늘"과 같지만, 주간·월간이 도입되면 같은 필드가 그대로
     * "이번 주"·"이번 달"을 뜻하게 되어 <b>클라이언트를 고치지 않아도 된다.</b>
     * 구간의 정의는 {@link RoutineCycle} 에 있다(주는 일요일 시작).
     */
    @Builder
    public record Detail(
            Long challengeId,
            String name,
            String description,
            String imageUrl,
            ChallengeCategory category,
            RoutineCycle routineCycle,
            int reward,
            boolean participating,
            boolean verifiedInCurrentPeriod,
            long participantCount,
            long verificationPostCount,
            long todayCompletionCount
    ) {
    }
}
