package com.lirouti.domain.verification.repository;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ChallengeVerificationLikeRepositoryCustom {

    /**
     * 인증 id별 좋아요 수를 한 번에 센다.
     *
     * 피드는 페이지당 여러 건을 내려주므로 건별 집계는 그대로 N+1이 된다. 페이지의 인증 id
     * 목록으로 GROUP BY 한 번에 묶는다. UNIQUE(challenge_verification_id, member_id)의
     * 선두 컬럼이 challenge_verification_id라 이 인덱스를 그대로 탄다.
     *
     * 탈퇴 회원의 좋아요는 제외한다 — 참여자 수·인증 게시글 수 집계와 같은 규칙이다
     * (database-schema.md). 좋아요가 0인 인증은 결과 맵에 나오지 않으므로 호출부가 기본값을 쓴다.
     */
    Map<Long, Long> countByVerificationIds(List<Long> verificationIds);

    /**
     * 그 회원이 좋아요를 누른 인증 id만 골라낸다.
     *
     * 집계와 같은 이유로 건별 exists 호출을 하지 않는다. 한 번에 받아 Set으로 들고 매핑한다.
     * 조회자 본인이므로 탈퇴 여부를 따지지 않는다(탈퇴하면 로그인 자체가 막힌다).
     */
    Set<Long> findLikedVerificationIds(List<Long> verificationIds, Long memberId);
}
