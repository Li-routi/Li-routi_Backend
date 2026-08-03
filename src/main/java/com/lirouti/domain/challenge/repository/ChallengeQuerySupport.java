package com.lirouti.domain.challenge.repository;

import com.lirouti.domain.verification.entity.QChallengeVerification;
import com.lirouti.domain.member.entity.QMember;
import com.querydsl.core.types.dsl.BooleanExpression;

/**
 * 챌린지 QueryDSL 구현들이 공유하는 조건.
 * 같은 규칙을 각 Impl에 복사해 두면 한쪽만 고쳐져 집계와 조회의 기준이 어긋나므로 여기에 모은다.
 */
public final class ChallengeQuerySupport {
    private ChallengeQuerySupport() {
    }

    /**
     * 집계·조회에서 제외할 회원: 비활성(탈퇴)이거나 소프트 삭제된 회원.
     * #18(회원 탈퇴)이 두 플래그를 어떻게 세팅하든 누수가 없도록 둘 다 확인한다.
     */
    public static BooleanExpression activeMember(QMember member) {
        return member.isActive.isTrue().and(member.deletedAt.isNull());
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
