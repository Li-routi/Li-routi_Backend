package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.QGroup;
import com.lirouti.domain.group.entity.QGroupMember;
import com.lirouti.domain.group.entity.QGroupRoutine;
import com.lirouti.domain.group.entity.QGroupRoutineAssignment;
import com.lirouti.domain.group.entity.QGroupRoutineSchedule;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.enums.GroupStatus;
import com.lirouti.domain.verification.entity.QGroupRoutineVerification;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/** 로그인 회원의 참여 그룹 목록 화면에 필요한 읽기 모델을 고정 횟수 배치 조회한다. */
@Repository
@RequiredArgsConstructor
public class GroupListQueryRepository {
    private static final QGroup group = QGroup.group;
    private static final QGroupMember groupMember = QGroupMember.groupMember;
    private static final QGroupRoutine routine = QGroupRoutine.groupRoutine;
    private static final QGroupRoutineAssignment assignment =
            QGroupRoutineAssignment.groupRoutineAssignment;
    private static final QGroupRoutineSchedule schedule =
            QGroupRoutineSchedule.groupRoutineSchedule;
    private static final QGroupRoutineVerification verification =
            QGroupRoutineVerification.groupRoutineVerification;

    private final JPAQueryFactory queryFactory;

    /** 조회 회원이 현재 ACTIVE 상태로 참여 중인 ACTIVE 그룹을 가입 최신순으로 조회한다. */
    public List<MyGroupProjection> findActiveGroupsByMemberId(Long memberId) {
        return queryFactory
                .select(Projections.constructor(
                        MyGroupProjection.class,
                        group.id,
                        group.name,
                        groupMember.currentStreak
                ))
                .from(groupMember)
                .join(groupMember.group, group)
                .where(
                        groupMember.member.id.eq(memberId),
                        groupMember.status.eq(GroupMemberStatus.ACTIVE),
                        group.status.eq(GroupStatus.ACTIVE)
                )
                .orderBy(groupMember.joinedAt.desc(), groupMember.id.desc())
                .fetch();
    }

    /** 그룹별 ACTIVE 참여 관계 중 활성·미삭제 계정의 구성원 수를 일괄 집계한다. */
    public List<GroupCountProjection> countActiveMembersByGroupIds(List<Long> groupIds) {
        return queryFactory
                .select(Projections.constructor(
                        GroupCountProjection.class,
                        groupMember.group.id,
                        groupMember.id.count()
                ))
                .from(groupMember)
                .where(
                        groupMember.group.id.in(groupIds),
                        groupMember.status.eq(GroupMemberStatus.ACTIVE),
                        groupMember.member.isActive.isTrue(),
                        groupMember.member.deletedAt.isNull()
                )
                .groupBy(groupMember.group.id)
                .fetch();
    }

    /** 그룹별 현재 활성 그룹 루틴 수를 일괄 집계한다. */
    public List<GroupCountProjection> countActiveRoutinesByGroupIds(List<Long> groupIds) {
        return queryFactory
                .select(Projections.constructor(
                        GroupCountProjection.class,
                        routine.group.id,
                        routine.id.count()
                ))
                .from(routine)
                .where(
                        routine.group.id.in(groupIds),
                        routine.active.isTrue()
                )
                .groupBy(routine.group.id)
                .fetch();
    }

    /** 조회 회원의 오늘 실제 할당과 완료 수를 현재 가입 회차·활성 루틴 기준으로 집계한다. */
    public List<AssignmentCountProjection> findTodayAssignmentCounts(
            Long memberId,
            List<Long> groupIds,
            LocalDate today
    ) {
        return assignmentCounts(memberId, groupIds, assignment.assignedDate.eq(today));
    }

    /** 조회 회원의 이번 달 월초부터 오늘까지 실제 할당과 완료 수를 집계한다. */
    public List<AssignmentCountProjection> findMonthlyAssignmentCounts(
            Long memberId,
            List<Long> groupIds,
            LocalDate monthStart,
            LocalDate today
    ) {
        return assignmentCounts(memberId, groupIds, assignment.assignedDate.between(monthStart, today));
    }

    /** 오늘자 활성 루틴 할당에 연결된 인증을 작성자의 현재 ACTIVE 가입 회차 기준으로 집계한다. */
    public List<GroupCountProjection> countTodayVerificationsByGroupIds(
            List<Long> groupIds,
            LocalDate today
    ) {
        return queryFactory
                .select(Projections.constructor(
                        GroupCountProjection.class,
                        group.id,
                        verification.id.count()
                ))
                .from(verification)
                .join(verification.assignment, assignment)
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
                        group.id.in(groupIds),
                        group.status.eq(GroupStatus.ACTIVE),
                        routine.active.isTrue(),
                        assignment.assignedDate.eq(today)
                )
                .groupBy(group.id)
                .fetch();
    }

    /** 미래 예정 횟수 계산에 필요한 현재 활성 루틴의 그룹·요일별 일정 수를 집계한다. */
    public List<GroupScheduleCountProjection> countActiveSchedulesByGroupIds(List<Long> groupIds) {
        return queryFactory
                .select(Projections.constructor(
                        GroupScheduleCountProjection.class,
                        routine.group.id,
                        schedule.repeatDay,
                        schedule.id.count()
                ))
                .from(schedule)
                .join(schedule.groupRoutine, routine)
                .where(
                        routine.group.id.in(groupIds),
                        routine.active.isTrue()
                )
                .groupBy(routine.group.id, schedule.repeatDay)
                .fetch();
    }

    private List<AssignmentCountProjection> assignmentCounts(
            Long memberId,
            List<Long> groupIds,
            com.querydsl.core.types.dsl.BooleanExpression dateCondition
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
                        AssignmentCountProjection.class,
                        group.id,
                        assignment.id.count(),
                        completedCount
                ))
                .from(assignment)
                .join(assignment.groupRoutine, routine)
                .join(routine.group, group)
                .join(groupMember).on(
                        groupMember.group.id.eq(group.id),
                        groupMember.member.id.eq(memberId),
                        groupMember.status.eq(GroupMemberStatus.ACTIVE),
                        assignment.createdAt.goe(groupMember.joinedAt)
                )
                .where(
                        assignment.member.id.eq(memberId),
                        group.id.in(groupIds),
                        group.status.eq(GroupStatus.ACTIVE),
                        routine.active.isTrue(),
                        dateCondition
                )
                .groupBy(group.id)
                .fetch();
    }

    public record MyGroupProjection(Long groupId, String groupName, int currentStreak) {
    }

    public record GroupCountProjection(Long groupId, long count) {
    }

    public record AssignmentCountProjection(Long groupId, long assignedCount, long completedCount) {
    }

    public record GroupScheduleCountProjection(Long groupId, DayOfWeek repeatDay, long count) {
    }
}
