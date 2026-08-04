package com.lirouti.domain.verification.repository;

import com.lirouti.domain.verification.entity.QChallengeVerification;
import com.querydsl.core.types.dsl.BooleanExpression;

/**
 * 인증 조회가 공유하는 QueryDSL 조건.
 *
 * <p>인증 엔티티를 보는 조건은 인증 도메인이 가진다. 챌린지 쪽에 두면 인증 엔티티를 참조하려고
 * 챌린지가 인증을 알고, 인증은 그 조건을 쓰려고 챌린지를 알게 되어 의존이 양방향이 된다.
 */
public final class VerificationQuerySupport {
    private VerificationQuerySupport() {
    }

    /**
     * 신고 누적으로 가려진 인증을 제외한다.
     *
     * <b>작성자 본인에게도 적용된다.</b> 본인만 보이게 하면 /me 조회만 예외가 되어 응답에
     * 상태 필드가 붙고 조회마다 분기가 생긴다. 임시 조치의 범위를 넘는다고 보아 조건 하나로
     * 통일했다(database-schema.md).
     *
     * <b>노출 경로에만 붙인다.</b> 스트릭과 오늘 완료자 수는 수행 기록이지 노출이 아니라서
     * 이 조건을 달지 않는다 — 신고당했다고 달성이 취소되면 안 된다.
     */
    public static BooleanExpression notHidden(QChallengeVerification verification) {
        return verification.hiddenAt.isNull();
    }
}
