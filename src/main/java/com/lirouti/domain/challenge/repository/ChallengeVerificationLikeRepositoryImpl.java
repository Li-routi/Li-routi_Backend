package com.lirouti.domain.challenge.repository;

import static com.lirouti.domain.challenge.repository.ChallengeQuerySupport.activeMember;
import static com.querydsl.core.group.GroupBy.groupBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lirouti.domain.challenge.entity.QChallengeVerificationLike;
import com.lirouti.domain.member.entity.QMember;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ChallengeVerificationLikeRepositoryImpl implements ChallengeVerificationLikeRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    private static final QChallengeVerificationLike like =
            QChallengeVerificationLike.challengeVerificationLike;
    private static final QMember member = QMember.member;

    @Override
    public Map<Long, Long> countByVerificationIds(List<Long> verificationIds) {
        if (verificationIds.isEmpty()) {
            return Map.of();
        }
        // 탈퇴 회원의 좋아요를 빼기 위해 member를 조인한다(참여자 수 집계와 같은 규칙).
        return queryFactory
                .select(like.challengeVerification.id, member.id.count())
                .from(like)
                .join(like.member, member)
                .where(
                        like.challengeVerification.id.in(verificationIds),
                        activeMember(member)
                )
                .groupBy(like.challengeVerification.id)
                .transform(groupBy(like.challengeVerification.id).as(member.id.count()));
    }

    @Override
    public Set<Long> findLikedVerificationIds(List<Long> verificationIds, Long memberId) {
        if (verificationIds.isEmpty() || memberId == null) {
            return Set.of();
        }
        // 조회자 본인의 행만 보므로 member 조인이 필요 없다. 유니크 제약을 그대로 탄다.
        return Set.copyOf(queryFactory
                .select(like.challengeVerification.id)
                .from(like)
                .where(
                        like.challengeVerification.id.in(verificationIds),
                        like.member.id.eq(memberId)
                )
                .fetch());
    }
}
