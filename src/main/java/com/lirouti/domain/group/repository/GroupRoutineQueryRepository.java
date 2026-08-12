package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.QGroup;
import com.lirouti.domain.group.entity.QGroupRoutine;
import com.lirouti.domain.group.entity.QGroupRoutineCategory;
import com.lirouti.domain.group.entity.QGroupRoutineSchedule;
import com.lirouti.domain.group.enums.GroupStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/** 특정 ACTIVE 그룹의 ACTIVE 루틴과 반복 일정을 분리 배치 조회하는 QueryDSL 읽기 모델이다. */
@Repository
@RequiredArgsConstructor
public class GroupRoutineQueryRepository {
    private static final QGroup group = QGroup.group;
    private static final QGroupRoutine routine = QGroupRoutine.groupRoutine;
    private static final QGroupRoutineCategory category = QGroupRoutineCategory.groupRoutineCategory;
    private static final QGroupRoutineSchedule schedule = QGroupRoutineSchedule.groupRoutineSchedule;

    private final JPAQueryFactory queryFactory;

    /** 루틴 본문을 생성 최신순으로 조회한다. 일정은 별도 배치 조회해 루틴 행 중복을 방지한다. */
    public List<GroupRoutineProjection> findActiveRoutinesByGroupId(Long groupId) {
        return queryFactory
                .select(Projections.constructor(
                        GroupRoutineProjection.class,
                        routine.id,
                        category.id,
                        category.name,
                        routine.title,
                        routine.description
                ))
                .from(routine)
                .join(routine.group, group)
                .join(routine.category, category)
                .where(
                        group.id.eq(groupId),
                        group.status.eq(GroupStatus.ACTIVE),
                        routine.active.isTrue()
                )
                .orderBy(routine.createdAt.desc(), routine.id.desc())
                .fetch();
    }

    /** 루틴 ID 목록에 속한 일정만 필요한 컬럼으로 한 번에 조회한다. */
    public List<RoutineScheduleProjection> findSchedulesByRoutineIds(List<Long> routineIds) {
        if (routineIds.isEmpty()) {
            return List.of();
        }

        return queryFactory
                .select(Projections.constructor(
                        RoutineScheduleProjection.class,
                        routine.id,
                        schedule.repeatDay,
                        schedule.startTime,
                        schedule.endTime
                ))
                .from(schedule)
                .join(schedule.groupRoutine, routine)
                .join(routine.group, group)
                .where(
                        routine.id.in(routineIds),
                        group.status.eq(GroupStatus.ACTIVE),
                        routine.active.isTrue()
                )
                .fetch();
    }

    /** 그룹 루틴 목록 응답 본문에 필요한 읽기 전용 projection이다. */
    public record GroupRoutineProjection(
            Long routineId,
            Long categoryId,
            String categoryName,
            String title,
            String description
    ) {
    }

    /** 그룹 루틴 목록 응답의 반복 일정 조립에 필요한 읽기 전용 projection이다. */
    public record RoutineScheduleProjection(
            Long routineId,
            DayOfWeek repeatDay,
            LocalTime startTime,
            LocalTime endTime
    ) {
    }
}
