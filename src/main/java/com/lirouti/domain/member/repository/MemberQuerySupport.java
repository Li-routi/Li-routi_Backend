package com.lirouti.domain.member.repository;

import com.lirouti.domain.member.entity.QMember;
import com.querydsl.core.types.dsl.BooleanExpression;

/**
 * 회원 상태를 보는 QueryDSL 조건. 회원을 조인해 거르는 곳이면 도메인을 가리지 않고 여기를 쓴다.
 *
 * <p>같은 규칙을 각 Impl에 복사해 두면 한쪽만 고쳐져 집계와 조회의 기준이 어긋난다.
 */
public final class MemberQuerySupport {
    private MemberQuerySupport() {
    }

    /**
     * 집계·조회에서 제외할 회원: 비활성(탈퇴)이거나 소프트 삭제된 회원.
     * 탈퇴 처리가 두 플래그를 어떻게 세팅하든 누수가 없도록 둘 다 확인한다.
     */
    public static BooleanExpression activeMember(QMember member) {
        return member.isActive.isTrue().and(member.deletedAt.isNull());
    }
}
