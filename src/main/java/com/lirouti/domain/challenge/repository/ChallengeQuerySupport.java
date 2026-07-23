package com.lirouti.domain.challenge.repository;

import com.lirouti.domain.member.entity.QMember;
import com.querydsl.core.types.dsl.BooleanExpression;

/**
 * 챌린지 QueryDSL 구현들이 공유하는 조건.
 * 같은 규칙을 각 Impl에 복사해 두면 한쪽만 고쳐져 집계와 조회의 기준이 어긋나므로 여기에 모은다.
 */
final class ChallengeQuerySupport {
    private ChallengeQuerySupport() {
    }

    /**
     * 집계·조회에서 제외할 회원: 비활성(탈퇴)이거나 소프트 삭제된 회원.
     * #18(회원 탈퇴)이 두 플래그를 어떻게 세팅하든 누수가 없도록 둘 다 확인한다.
     */
    static BooleanExpression activeMember(QMember member) {
        return member.isActive.isTrue().and(member.deletedAt.isNull());
    }
}
