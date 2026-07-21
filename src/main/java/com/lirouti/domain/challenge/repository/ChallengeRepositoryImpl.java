package com.lirouti.domain.challenge.repository;

import static com.querydsl.core.group.GroupBy.groupBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.lirouti.domain.challenge.entity.Challenge;
import com.lirouti.domain.challenge.entity.QChallenge;
import com.lirouti.domain.challenge.entity.QChallengeVerification;
import com.lirouti.domain.challenge.entity.QMemberChallenge;
import com.lirouti.domain.challenge.enums.ChallengeCategory;
import com.lirouti.domain.member.entity.QMember;
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
    public List<Challenge> findByCursor(
            ChallengeCategory category,
            String keyword,
            Long cursor,
            int limit
    ) {
        // 최신순 커서 페이지네이션. id는 auto-increment라 최신일수록 크므로 id 내림차순이 곧 최신순이다.
        // cursor(마지막으로 받은 challengeId)가 있으면 그보다 작은 id만 가져와 이어서 스크롤한다.
        return queryFactory
                .selectFrom(challenge)
                .where(
                        challenge.active.isTrue(),
                        categoryEq(category),
                        nameContains(keyword),
                        cursorLt(cursor)
                )
                .orderBy(challenge.id.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public Map<Long, Long> countActiveParticipantsByChallengeIds(List<Long> challengeIds) {
        if (challengeIds.isEmpty()) {
            return Map.of();
        }
        // challenge_id별 활성 참여자 수. 탈퇴 회원 제외. 참여자 0인 챌린지는 결과에 안 나온다.
        return queryFactory
                .select(memberChallenge.challenge.id, member.id.count())
                .from(memberChallenge)
                .join(memberChallenge.member, member)
                .where(
                        memberChallenge.challenge.id.in(challengeIds),
                        memberChallenge.active.isTrue(),
                        activeMember()
                )
                .groupBy(memberChallenge.challenge.id)
                .transform(groupBy(memberChallenge.challenge.id).as(member.id.count()));
    }

    @Override
    public Map<Long, Long> countVerificationPostsByChallengeIds(List<Long> challengeIds) {
        if (challengeIds.isEmpty()) {
            return Map.of();
        }
        // challenge_id별 인증 게시글 수. 인증(게시글) 단위이므로 회차 중복 제거를 하지 않는다.
        // 탈퇴 회원의 인증은 제외한다.
        return queryFactory
                .select(memberChallenge.challenge.id, verification.id.count())
                .from(verification)
                .join(verification.memberChallenge, memberChallenge)
                .join(memberChallenge.member, member)
                .where(
                        memberChallenge.challenge.id.in(challengeIds),
                        activeMember()
                )
                .groupBy(memberChallenge.challenge.id)
                .transform(groupBy(memberChallenge.challenge.id).as(verification.id.count()));
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
                        activeMember()
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
                        // 현재 회차의 오늘 인증만. 참여 중(active) 여부는 조건에 넣지 않는다
                        // — 오늘 인증한 뒤 그만둔 사람도 '오늘 완료자'로 집계한다(스키마 규칙).
                        verification.participationRound.eq(memberChallenge.participationRound),
                        activeMember()
                )
                .fetchOne();
        return (count != null) ? count : 0L;
    }

    // 집계에서 제외할 회원: 비활성(탈퇴)이거나 소프트 삭제된 회원.
    // #18(회원 탈퇴)이 두 플래그를 어떻게 세팅하든 누수가 없도록 둘 다 확인한다.
    private BooleanExpression activeMember() {
        return member.isActive.isTrue().and(member.deletedAt.isNull());
    }

    private BooleanExpression cursorLt(Long cursor) {
        return (cursor != null) ? challenge.id.lt(cursor) : null;
    }

    private BooleanExpression categoryEq(ChallengeCategory category) {
        return (category != null) ? challenge.category.eq(category) : null;
    }

    private BooleanExpression nameContains(String keyword) {
        return (keyword != null && !keyword.isBlank()) ? challenge.name.contains(keyword.trim()) : null;
    }
}
