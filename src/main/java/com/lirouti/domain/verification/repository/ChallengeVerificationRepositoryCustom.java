package com.lirouti.domain.verification.repository;

import com.lirouti.domain.verification.entity.ChallengeVerification;

import java.util.List;

public interface ChallengeVerificationRepositoryCustom {

    /**
     * 한 챌린지의 최신 인증 피드를 커서 기반으로 조회한다(무한 스크롤용).
     *
     * cursor가 null이면 첫 페이지(가장 최신부터), 값이 있으면 그 verificationId보다 이전 것만 가져온다.
     * 탈퇴 회원의 인증은 제외한다.
     *
     * 정렬·커서 키는 verified_at이 아니라 id다. 당일 재인증이 verified_at을 덮어쓰기 때문에,
     * verified_at을 커서로 쓰면 페이지를 넘기는 도중 항목이 위로 점프해 중복·누락이 생긴다.
     *
     * 회차 중복 제거를 하지 않는다 — 인증(게시글) 단위 나열이므로 같은 날 이탈 후 재참여해 다시
     * 인증한 두 건은 실제로 별개의 인증 이벤트다(database-schema.md).
     *
     * 참여 상태(active)도 조건에 넣지 않는다. 인증한 뒤 그만둔 사람의 인증도 남아 있는 게 맞다.
     *
     * viewerId가 신고한 인증은 제외한다. 신고는 인증을 지우지 않는다. viewerId가 null이면
     * 이 조건을 걸지 않는다.
     *
     * 신고가 임계값만큼 쌓여 전체에게 가려지는 것은 별개 조건(hiddenAt)이 처리하며,
     * 그쪽은 조회자가 누구든 적용된다.
     */
    List<ChallengeVerification> findFeedByCursor(
            Long challengeId,
            Long viewerId,
            Long cursor,
            int limit
    );

    /**
     * 한 회원이 그 챌린지에서 남긴 인증을 커서 기반으로 조회한다.
     *
     * <p><b>회차로 좁히지 않는다.</b> 예전에는 현재 회차만 돌려줬는데, 그러면 재참여한 뒤
     * 지난 회차 인증이 <b>내 목록에서만</b> 사라졌다 — 피드에는 그대로 남고 {@code mine} 까지
     * true 로 표시되므로, 내 글이라고 표시되는데 내 목록엔 없는 상태가 됐다.
     * 회차는 되돌아가지 않으므로 그 인증은 영영 다시 보이지 않았다.
     *
     * <p>이탈이 기록을 지우지 않는다는 원칙(database-schema.md: 참여 이력을 보존하므로
     * 소프트 삭제를 쓰지 않는다)과도 어긋났다. 회차는 응답에 실어 클라이언트가
     * "이번 참여 / 지난 참여"로 구분하게 한다.
     *
     * <p>조인이 없다. memberChallengeId 를 이미 알고 들어오므로
     * UNIQUE(member_challenge_id, participation_round, verified_date) 의 선두 컬럼을 타고,
     * 인덱스를 새로 만들 필요가 없다.
     *
     * <p>신고 필터를 걸지 않는다 — 내가 내 인증을 신고할 일이 없다.
     * 정렬·커서 키가 id 인 이유는 피드와 같다(당일 재인증이 verified_at 을 덮어써서 순서가 흔들린다).
     * <b>회차 조건을 뺀 덕에 커서가 더 안정적이다</b> — 스크롤 도중 재참여해도 다음 페이지가
     * 비지 않는다(예전에는 커서가 지난 회차 id 라 새 회차 인증과 겹치지 않았다).
     */
    List<ChallengeVerification> findMineByCursor(
            Long memberChallengeId,
            Long cursor,
            int limit
    );
}
