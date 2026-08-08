package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.QGroup;
import com.lirouti.domain.group.entity.QGroupMember;
import com.lirouti.domain.group.entity.QGroupRoutine;
import com.lirouti.domain.group.entity.QGroupRoutineAssignment;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.enums.GroupStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/** 그룹방 진입 화면에 필요한 읽기 모델을 QueryDSL로 일괄 조회한다. */
@Repository
@RequiredArgsConstructor
public class GroupDetailQueryRepository {
    private static final QGroup group = QGroup.group;
    private static final QGroupMember groupMember = QGroupMember.groupMember;
    private static final QGroupRoutine routine = QGroupRoutine.groupRoutine;
    private static final QGroupRoutineAssignment assignment =
            QGroupRoutineAssignment.groupRoutineAssignment;

    private final JPAQueryFactory queryFactory;

    /** ACTIVE 그룹원과 그룹방 기본 정보를 한 번에 가져온다. */
    public List<GroupMemberDetailProjection> findActiveMemberDetails(Long groupId) {
        return queryFactory
                .select(Projections.constructor(
                        GroupMemberDetailProjection.class,
                        group.id,
                        group.name,
                        group.inviteCode,
                        groupMember.member.id,
                        groupMember.member.nickname,
                        groupMember.member.profileImageKey,
                        groupMember.statusMessage,
                        groupMember.currentStreak,
                        groupMember.totalLikeCount
                ))
                .from(groupMember)
                .join(groupMember.group, group)
                .where(
                        group.id.eq(groupId),
                        group.status.eq(GroupStatus.ACTIVE),
                        groupMember.status.eq(GroupMemberStatus.ACTIVE),
                        groupMember.member.isActive.isTrue(),
                        groupMember.member.deletedAt.isNull()
                )
                .orderBy(groupMember.id.asc())
                .fetch();
    }

    /** 오늘 ACTIVE 그룹원에게 남은 실제 할당을 멤버별 완료/전체 수로 집계한다. */
    public List<TodayMemberProgressProjection> findTodayMemberProgress(
            Long groupId,
            LocalDate assignedDate
    ) {
        NumberExpression<Long> completedCount = Expressions.numberTemplate(
                Long.class,
                "sum({0})",
                new CaseBuilder()
                        .when(assignment.status.eq(GroupRoutineAssignmentStatus.COMPLETED))
                        .then(1L)
                        .otherwise(0L)
        );
        return queryFactory
                .select(Projections.constructor(
                        TodayMemberProgressProjection.class,
                        assignment.member.id,
                        assignment.id.count(),
                        completedCount
                ))
                .from(assignment)
                .join(assignment.groupRoutine, routine)
                .join(routine.group, group)
                .join(groupMember).on(
                        groupMember.group.id.eq(group.id),
                        groupMember.member.id.eq(assignment.member.id),
                        groupMember.status.eq(GroupMemberStatus.ACTIVE),
                        groupMember.member.isActive.isTrue(),
                        groupMember.member.deletedAt.isNull(),
                        assignment.createdAt.goe(groupMember.joinedAt)
                )
                .where(
                        group.id.eq(groupId),
                        group.status.eq(GroupStatus.ACTIVE),
                        routine.active.isTrue(),
                        assignment.assignedDate.eq(assignedDate)
                )
                .groupBy(assignment.member.id)
                .fetch();
    }

    public record GroupMemberDetailProjection(
            Long groupId,
            String groupName,
            String inviteCode,
            Long memberId,
            String name,
            String profileImageKey,
            String statusMessage,
            int currentStreak,
            long totalLikeCount
    ) {
    }

    public record TodayMemberProgressProjection(
            Long memberId,
            long totalCount,
            long completedCount
    ) {
    }
}
