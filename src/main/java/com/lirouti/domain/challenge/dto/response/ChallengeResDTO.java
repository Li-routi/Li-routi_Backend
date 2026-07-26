package com.lirouti.domain.challenge.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.enums.RoutineCycle;

import lombok.Builder;

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
    @Builder
    public record Summary(
            Long challengeId,
            String name,
            String description,
            String imageUrl,
            ChallengeCategory category,
            RoutineCycle routineCycle,
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

    // 피드 카드 한 건. 닉네임·사진·코멘트를 보여준다.
    @Builder
    public record FeedItem(
            Long verificationId,
            String nickname,
            String imageUrl,
            String content,
            LocalDateTime verifiedAt
    ) {
    }

    // 상세 화면
    // participating: 조회자가 현재 참여 중인지. 비로그인이면 false('참여하기' 버튼 노출).
    // verificationPostCount: 인증 게시글 수(상단 통계). participantCount와 함께 카드 상단에 쓰인다.
    @Builder
    public record Detail(
            Long challengeId,
            String name,
            String description,
            String imageUrl,
            ChallengeCategory category,
            RoutineCycle routineCycle,
            boolean participating,
            long participantCount,
            long verificationPostCount,
            long todayCompletionCount
    ) {
    }
}
