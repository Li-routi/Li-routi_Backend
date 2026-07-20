package com.lirouti.domain.challenge.dto.response;

import java.util.List;

import com.lirouti.domain.challenge.enums.ChallengeCategory;

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

    // 목록 카드 한 건. 참여자 수는 이 화면에 표시하지 않으므로 담지 않는다(상세에서 제공).
    @Builder
    public record Summary(
            Long challengeId,
            String name,
            String description,
            String imageUrl,
            ChallengeCategory category
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
            long participantCount,
            long todayCompletionCount
    ) {
    }
}
