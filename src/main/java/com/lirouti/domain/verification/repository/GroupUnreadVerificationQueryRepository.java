package com.lirouti.domain.verification.repository;

import com.lirouti.domain.group.entity.QGroup;
import com.lirouti.domain.group.entity.QGroupRoutine;
import com.lirouti.domain.group.entity.QGroupRoutineAssignment;
import com.lirouti.domain.member.entity.QMember;
import com.lirouti.domain.verification.entity.QGroupRoutineVerification;
import com.lirouti.domain.verification.entity.QGroupRoutineVerificationReread;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/** 그룹방 진입 후 아직 읽지 않은 타인 인증을 필요한 화면 컬럼만 조회한다. */
@Repository
@RequiredArgsConstructor
public class GroupUnreadVerificationQueryRepository {
    private static final QGroupRoutineVerification verification =
            QGroupRoutineVerification.groupRoutineVerification;
    private static final QGroupRoutineVerificationReread reread =
            QGroupRoutineVerificationReread.groupRoutineVerificationReread;
    private static final QGroupRoutineAssignment assignment =
            QGroupRoutineAssignment.groupRoutineAssignment;
    private static final QGroupRoutine routine = QGroupRoutine.groupRoutine;
    private static final QGroup group = QGroup.group;
    private static final QMember author = new QMember("author");

    private final JPAQueryFactory queryFactory;

    public List<UnreadVerificationProjection> findUnreadByCursor(
            Long groupId,
            Long viewerId,
            LocalDateTime membershipStartOfDay,
            Long lastReadVerificationId,
            Long cursor,
            Limit limit
    ) {
        return queryFactory
                .select(Projections.constructor(
                        UnreadVerificationProjection.class,
                        verification.id,
                        author.id,
                        author.nickname,
                        routine.title,
                        verification.imageUrl,
                        verification.content,
                        verification.verifiedAt
                ))
                .from(verification)
                .join(verification.assignment, assignment)
                .join(assignment.groupRoutine, routine)
                .join(routine.group, group)
                .join(assignment.member, author)
                .where(
                        group.id.eq(groupId),
                        assignment.member.id.ne(viewerId),
                        verification.createdAt.goe(membershipStartOfDay),
                        lastReadVerificationId == null
                                ? null : verification.id.gt(lastReadVerificationId),
                        cursor == null ? null : verification.id.gt(cursor)
                )
                .orderBy(verification.id.asc())
                .limit(limit.max())
                .fetch();
    }

    /**
     * 이미 읽은 뒤 재인증된 인증글만 marker에서 찾아온다.
     *
     * <p>신규 인증의 {@code id > lastReadVerificationId} 조건을 의도적으로 넣지 않는다. 이
     * 경로의 대상은 그 커서보다 작거나 같은 과거 ID이기 때문이다.
     */
    public List<UnreadVerificationProjection> findRereadByCursor(
            Long groupId,
            Long viewerId,
            LocalDateTime membershipStartOfDay,
            Long lastReadVerificationId,
            Long cursor,
            Limit limit
    ) {
        if (lastReadVerificationId == null) {
            return List.of();
        }

        return queryFactory
                .select(Projections.constructor(
                        UnreadVerificationProjection.class,
                        verification.id,
                        author.id,
                        author.nickname,
                        routine.title,
                        verification.imageUrl,
                        verification.content,
                        verification.verifiedAt
                ))
                .from(reread)
                .join(verification).on(verification.id.eq(reread.verificationId))
                .join(verification.assignment, assignment)
                .join(assignment.groupRoutine, routine)
                .join(routine.group, group)
                .join(assignment.member, author)
                .where(
                        reread.group.id.eq(groupId),
                        reread.member.id.eq(viewerId),
                        group.id.eq(groupId),
                        assignment.member.id.ne(viewerId),
                        verification.createdAt.goe(membershipStartOfDay),
                        verification.id.loe(lastReadVerificationId),
                        cursor == null ? null : verification.id.gt(cursor)
                )
                .orderBy(verification.id.asc())
                .limit(limit.max())
                .fetch();
    }

    public record UnreadVerificationProjection(
            Long verificationId,
            Long authorMemberId,
            String authorName,
            String routineName,
            String imageKey,
            String content,
            LocalDateTime verifiedAt
    ) {
    }
}
