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
     * viewerId가 신고한 인증은 제외한다(#15). 신고는 인증을 지우지 않고 신고자 본인의 피드에서만
     * 가리므로, 같은 인증이 다른 회원에게는 그대로 보인다. viewerId가 null이면 이 조건을 걸지 않는다.
     */
    List<ChallengeVerification> findFeedByCursor(
            Long challengeId,
            Long viewerId,
            Long cursor,
            int limit
    );

    /**
     * 한 회원이 그 챌린지에서 남긴 인증을 커서 기반으로 조회한다(#62).
     *
     * 피드와 달리 <b>회차로 범위를 좁힌다.</b> member_challenge는 이탈 후 재참여 시 같은 행을
     * 재활용하고 participation_round만 올리므로, 회차를 걸지 않으면 지난 참여의 인증까지 섞인다.
     * 스트릭·오늘 완료 여부가 모두 현재 회차 기준이라(database-schema.md) 목록도 같은 기준으로 둔다.
     *
     * 조인이 없다. memberChallengeId를 이미 알고 들어오므로 UNIQUE(member_challenge_id,
     * participation_round, verified_date)의 선두 두 컬럼을 그대로 타고, 인덱스를 새로 만들 필요가 없다.
     *
     * 신고 필터(#15)를 걸지 않는다 — 내가 내 인증을 신고할 일이 없다.
     * 정렬·커서 키가 id인 이유는 피드와 같다(당일 재인증이 verified_at을 덮어써서 순서가 흔들린다).
     */
    List<ChallengeVerification> findMineByCursor(
            Long memberChallengeId,
            int participationRound,
            Long cursor,
            int limit
    );
}
