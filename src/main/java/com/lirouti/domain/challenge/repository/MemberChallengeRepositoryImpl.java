package com.lirouti.domain.challenge.repository;

import java.util.List;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.QChallenge;
import com.lirouti.domain.challenge.entity.QMemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MemberChallengeRepositoryImpl implements MemberChallengeRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    private static final QMemberChallenge memberChallenge = QMemberChallenge.memberChallenge;
    private static final QChallenge challenge = QChallenge.challenge;

    @Override
    public List<Challenge> findMyActiveChallenges(Long memberId, ChallengeCategory category, String keyword) {
        return queryFactory
                .select(challenge)
                .from(memberChallenge)
                .join(memberChallenge.challenge, challenge)
                .where(
                        memberChallenge.member.id.eq(memberId),
                        memberChallenge.active.isTrue(),
                        challenge.active.isTrue(),
                        categoryEq(category),
                        nameContains(keyword)
                )
                .orderBy(memberChallenge.joinedAt.desc(), memberChallenge.id.desc())
                .fetch();
    }

    private BooleanExpression categoryEq(ChallengeCategory category) {
        return (category != null) ? challenge.category.eq(category) : null;
    }

    private BooleanExpression nameContains(String keyword) {
        return (keyword != null && !keyword.isBlank()) ? challenge.name.contains(keyword.trim()) : null;
    }
}
