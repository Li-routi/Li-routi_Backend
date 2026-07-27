package com.lirouti.domain.challenge.repository;

import static com.lirouti.domain.challenge.repository.ChallengeQuerySupport.activeMember;

import java.util.List;

import com.lirouti.domain.challenge.entity.ChallengeVerification;
import com.lirouti.domain.challenge.entity.QChallengeVerification;
import com.lirouti.domain.challenge.entity.QChallengeVerificationReport;
import com.lirouti.domain.challenge.entity.QMemberChallenge;
import com.lirouti.domain.member.entity.QMember;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ChallengeVerificationRepositoryImpl implements ChallengeVerificationRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    private static final QChallengeVerification verification = QChallengeVerification.challengeVerification;
    private static final QMemberChallenge memberChallenge = QMemberChallenge.memberChallenge;
    private static final QMember member = QMember.member;
    private static final QChallengeVerificationReport report =
            QChallengeVerificationReport.challengeVerificationReport;

    @Override
    public List<ChallengeVerification> findFeedByCursor(
            Long challengeId,
            Long viewerId,
            Long cursor,
            int limit
    ) {
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
                        notReportedBy(viewerId),
                        cursorLt(cursor)
                )
                .orderBy(verification.id.desc())
                .limit(limit)
                .fetch();
    }

    /**
     * 조회자가 신고한 인증을 제외한다(#15). 신고는 인증을 지우지 않고 <b>신고자 본인에게만</b> 가린다.
     *
     * 조인이 아니라 NOT EXISTS를 쓰는 이유는 두 가지다. 조인은 신고가 없는 인증을 걸러내려면
     * left join + is null이 되어 fetch join과 섞였을 때 읽기 어려워지고, 한 인증에 신고가
     * 여러 건이면 행이 부풀어 limit이 어긋난다. NOT EXISTS는 유니크 제약
     * (challenge_verification_id, reporter_id)의 앞 두 컬럼을 그대로 타므로 인덱스도 쓴다.
     *
     * viewerId가 null이면 조건을 걸지 않는다. 지금 피드는 로그인 필수라 null이 오지 않지만,
     * 조건 자체는 비로그인 조회가 열려도 그대로 동작하게 둔다.
     */
    private BooleanExpression notReportedBy(Long viewerId) {
        if (viewerId == null) {
            return null;
        }
        return JPAExpressions
                .selectOne()
                .from(report)
                .where(
                        report.challengeVerification.eq(verification),
                        report.reporter.id.eq(viewerId)
                )
                .notExists();
    }

    private BooleanExpression cursorLt(Long cursor) {
        return (cursor != null) ? verification.id.lt(cursor) : null;
    }
}
