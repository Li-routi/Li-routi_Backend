package com.lirouti.domain.verification.repository;

import static com.querydsl.core.group.GroupBy.groupBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lirouti.domain.verification.entity.QGroupRoutineVerificationLike;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GroupRoutineVerificationLikeRepositoryImpl
        implements GroupRoutineVerificationLikeRepositoryCustom {
    private static final QGroupRoutineVerificationLike like =
            QGroupRoutineVerificationLike.groupRoutineVerificationLike;

    private final JPAQueryFactory queryFactory;

    @Override
    public Map<Long, Long> countByVerificationIds(List<Long> verificationIds) {
        if (verificationIds.isEmpty()) {
            return Map.of();
        }
        // 탈퇴·강제 퇴장·비활성 Like 작성자의 행도 이력과 수에 남긴다.
        return queryFactory
                .select(like.groupRoutineVerification.id, like.id.count())
                .from(like)
                .where(like.groupRoutineVerification.id.in(verificationIds))
                .groupBy(like.groupRoutineVerification.id)
                .transform(groupBy(like.groupRoutineVerification.id).as(like.id.count()));
    }

    @Override
    public Set<Long> findLikedVerificationIds(List<Long> verificationIds, Long memberId) {
        if (verificationIds.isEmpty() || memberId == null) {
            return Set.of();
        }
        return Set.copyOf(queryFactory
                .select(like.groupRoutineVerification.id)
                .from(like)
                .where(
                        like.groupRoutineVerification.id.in(verificationIds),
                        like.member.id.eq(memberId)
                )
                .fetch());
    }
}
