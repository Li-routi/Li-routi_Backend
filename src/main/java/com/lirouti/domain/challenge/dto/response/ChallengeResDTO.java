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
            long verificationPostCount
    ) {
    }
}
