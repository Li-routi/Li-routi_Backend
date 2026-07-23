package com.lirouti.domain.challenge.repository;

import java.util.List;

import com.lirouti.domain.challenge.entity.ChallengeVerification;

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
     * TODO(#15): 신고 숨김이 들어오면 조회자 본인이 신고한 인증을 제외하는 조건을 여기에 추가한다.
     *  지금은 쓰이지 않을 조회자 파라미터를 미리 두지 않고, 실제로 필요해질 때 함께 넣는다.
     */
    List<ChallengeVerification> findFeedByCursor(Long challengeId, Long cursor, int limit);
}
