package com.lirouti.domain.verification.repository;

import com.lirouti.domain.group.entity.QGroup;
import com.lirouti.domain.group.entity.QGroupRoutine;
import com.lirouti.domain.group.entity.QGroupRoutineAssignment;
import com.lirouti.domain.member.entity.QMember;
import com.lirouti.domain.verification.entity.QGroupRoutineVerification;
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
