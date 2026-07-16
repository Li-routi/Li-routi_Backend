package com.lirouti.domain.challenge.repository;

import java.time.LocalDate;
import java.util.List;

import com.lirouti.domain.challenge.entity.QChallenge;
import com.lirouti.domain.challenge.entity.QChallengeVerification;
import com.lirouti.domain.challenge.entity.QMemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.challenge.enums.ChallengeSortType;
import com.lirouti.domain.member.entity.QMember;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ChallengeRepositoryImpl implements ChallengeRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    private static final QChallenge challenge = QChallenge.challenge;
    private static final QMemberChallenge memberChallenge = QMemberChallenge.memberChallenge;
    private static final QChallengeVerification verification = QChallengeVerification.challengeVerification;
    private static final QMember member = QMember.member;

    @Override
    public List<ChallengeSummaryProjection> findSummaries(
            ChallengeCategory category,
            String keyword,
            ChallengeSortType sort
    ) {
        // 참여자 수는 활성 참여(mc.active) + 활성 회원(m.isActive)만 센다.
        // 두 조건을 JOIN ON에 둬야, 참여자가 0인 챌린지도 목록에서 빠지지 않는다(WHERE에 두면 사라짐).
        return queryFactory
                .select(Projections.constructor(
                        ChallengeSummaryProjection.class,
                        challenge,
                        member.id.count()
                ))
                .from(challenge)
                .leftJoin(memberChallenge)
                .on(memberChallenge.challenge.eq(challenge)
                        .and(memberChallenge.active.isTrue()))
                .leftJoin(memberChallenge.member, member)
                .on(member.isActive.isTrue())
                .where(
                        challenge.active.isTrue(),
                        categoryEq(category),
                        nameContains(keyword)
                )
                .groupBy(challenge.id)
                .orderBy(orderBy(sort))
                .fetch();
    }

    // 정렬 키가 동점일 때 순서가 흔들리지 않도록 항상 id 내림차순을 마지막에 둔다.
    private OrderSpecifier<?>[] orderBy(ChallengeSortType sort) {
        if (sort == ChallengeSortType.LATEST) {
            return new OrderSpecifier<?>[]{challenge.createdAt.desc(), challenge.id.desc()};
        }
        // POPULAR(기본): 참여자 수 내림차순 → 동점 시 최신 id
        return new OrderSpecifier<?>[]{member.id.count().desc(), challenge.id.desc()};
    }

    @Override
    public long countActiveParticipants(Long challengeId) {
        Long count = queryFactory
                .select(member.id.count())
                .from(memberChallenge)
                .join(memberChallenge.member, member)
                .where(
                        memberChallenge.challenge.id.eq(challengeId),
                        memberChallenge.active.isTrue(),
                        member.isActive.isTrue()
                )
                .fetchOne();
        return (count != null) ? count : 0L;
    }

    @Override
    public long countTodayCompletions(Long challengeId, LocalDate today) {
        // 오늘 인증을 회원 단위로 센다. 회차가 다른 오늘자 인증이 두 건 생길 수 있으므로
        // member_challenge_id 기준으로 중복을 제거하고, 현재 회차의 인증만 포함한다.
        Long count = queryFactory
                .select(memberChallenge.id.countDistinct())
                .from(verification)
                .join(verification.memberChallenge, memberChallenge)
                .join(memberChallenge.member, member)
                .where(
                        memberChallenge.challenge.id.eq(challengeId),
                        verification.verifiedDate.eq(today),
                        verification.participationRound.eq(memberChallenge.participationRound),
                        memberChallenge.active.isTrue(),
                        member.isActive.isTrue()
                )
                .fetchOne();
        return (count != null) ? count : 0L;
    }

    private BooleanExpression categoryEq(ChallengeCategory category) {
        return (category != null) ? challenge.category.eq(category) : null;
    }

    private BooleanExpression nameContains(String keyword) {
        return (keyword != null && !keyword.isBlank()) ? challenge.name.contains(keyword.trim()) : null;
    }
}
