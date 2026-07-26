package com.lirouti.domain.challenge.repository;

import static com.lirouti.domain.challenge.repository.ChallengeQuerySupport.activeMember;

import java.util.List;

import com.lirouti.domain.challenge.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.QChallengeVerification;
import com.lirouti.domain.challenge.entity.QMemberChallenge;
import com.lirouti.domain.member.entity.QMember;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ChallengeVerificationRepositoryImpl implements ChallengeVerificationRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    private static final QChallengeVerification verification = QChallengeVerification.challengeVerification;
    private static final QMemberChallenge memberChallenge = QMemberChallenge.memberChallenge;
    private static final QMember member = QMember.member;

    @Override
    public List<ChallengeVerification> findFeedByCursor(Long challengeId, Long cursor, int limit) {
        // 참여(member_challenge)와 회원을 fetch join으로 함께 읽는다.
        // 피드 카드가 닉네임을 쓰므로, 없으면 항목마다 회원을 조회하는 N+1이 된다.
        // 둘 다 ToOne 연관이라 fetch join과 limit을 같이 써도 페이징이 메모리로 새지 않는다.
        return queryFactory
                .selectFrom(verification)
                .join(verification.memberChallenge, memberChallenge).fetchJoin()
                .join(memberChallenge.member, member).fetchJoin()
                .where(
                        memberChallenge.challenge.id.eq(challengeId),
                        activeMember(member),
                        cursorLt(cursor)
                )
                .orderBy(verification.id.desc())
                .limit(limit)
                .fetch();
    }

    private BooleanExpression cursorLt(Long cursor) {
        return (cursor != null) ? verification.id.lt(cursor) : null;
    }
}
