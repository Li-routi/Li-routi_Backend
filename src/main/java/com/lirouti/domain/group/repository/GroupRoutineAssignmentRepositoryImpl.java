package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.QGroup;
import com.lirouti.domain.group.entity.QGroupMember;
import com.lirouti.domain.group.entity.QGroupRoutine;
import com.lirouti.domain.group.entity.QGroupRoutineAssignment;
import com.lirouti.domain.group.entity.QRoutineCategory;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.enums.GroupStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GroupRoutineAssignmentRepositoryImpl
        implements GroupRoutineAssignmentRepositoryCustom {

    private static final QGroupRoutineAssignment assignment =
            QGroupRoutineAssignment.groupRoutineAssignment;
    private static final QGroupRoutine routine = QGroupRoutine.groupRoutine;
    private static final QGroup group = QGroup.group;
    private static final QRoutineCategory category = QRoutineCategory.routineCategory;
    private static final QGroupMember groupMember = QGroupMember.groupMember;

    private final JPAQueryFactory queryFactory;

    @Override
    public List<TodayAssignmentProjection> findTodayAssignmentsByMemberId(
            Long memberId,
            LocalDate assignedDate
    ) {
        return queryFactory
                .select(Projections.constructor(
                        TodayAssignmentProjection.class,
                        assignment.id,
                        routine.id,
                        group.id,
                        group.name,
                        category.id,
                        category.name,
                        routine.title,
                        routine.description,
                        assignment.assignedDate,
                        assignment.scheduledStartTime,
                        assignment.scheduledEndTime,
                        assignment.status
                ))
                .from(assignment)
                .join(assignment.groupRoutine, routine)
                .join(routine.group, group)
                .join(routine.category, category)
                .join(groupMember).on(
                        groupMember.group.id.eq(group.id),
                        groupMember.member.id.eq(memberId),
                        groupMember.status.eq(GroupMemberStatus.ACTIVE)
                )
                .where(
                        assignment.member.id.eq(memberId),
                        assignment.assignedDate.eq(assignedDate),
                        group.status.eq(GroupStatus.ACTIVE)
                )
                .orderBy(
                        assignment.scheduledStartTime.asc(),
                        assignment.id.asc()
                )
                .fetch();
    }
}
