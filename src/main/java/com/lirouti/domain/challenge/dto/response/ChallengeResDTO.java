package com.lirouti.domain.challenge.dto.response;

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

    // 상세 화면
    @Builder
    public record Detail(
            Long challengeId,
            String name,
            String description,
            String imageUrl,
            ChallengeCategory category,
            RoutineCycle routineCycle,
            long participantCount,
            long todayCompletionCount
    ) {
    }
}
