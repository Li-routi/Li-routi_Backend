package com.lirouti.domain.group.converter;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineSchedule;
import com.lirouti.domain.group.entity.RoutineCategory;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepositoryCustom.TodayAssignmentProjection;
import java.util.Comparator;
import java.util.List;

public final class GroupConverter {
    private GroupConverter() {
    }

    /**
     * 생성 요청과 검증된 그룹·카테고리를 일정이 연결된 그룹 루틴으로 변환한다.
     *
     * @param request 그룹 루틴 생성 요청
     * @param group 루틴이 속할 그룹
     * @param category 앱에서 관리하는 활성 카테고리
     * @return 요일별 일정이 연결된 그룹 루틴
     */
    public static GroupRoutine toGroupRoutine(
            GroupReqDTO.CreateRoutine request,
            Group group,
            RoutineCategory category
    ) {
        GroupRoutine groupRoutine = GroupRoutine.builder()
                .group(group)
                .category(category)
                .title(request.title())
                .description(request.description())
                .build();

        request.schedules().forEach(schedule -> groupRoutine.addSchedule(
                schedule.repeatDay(),
                schedule.startTime(),
                schedule.endTime()
        ));
        return groupRoutine;
    }

    /**
     * 저장된 그룹 루틴을 생성 응답으로 변환하고 일정을 요일 순서로 정렬한다.
     *
     * @param groupRoutine 저장된 그룹 루틴
     * @param assignmentCount 생성 당일 할당 대상 수
     * @return 그룹 루틴 생성 응답
     */
    public static GroupResDTO.RoutineCreateResult toRoutineCreateResult(
            GroupRoutine groupRoutine,
            int assignmentCount
    ) {
        List<GroupResDTO.RoutineSchedule> schedules = groupRoutine.getSchedules().stream()
                .sorted(Comparator.comparingInt(schedule -> schedule.getRepeatDay().getValue()))
                .map(GroupConverter::toRoutineSchedule)
                .toList();

        return GroupResDTO.RoutineCreateResult.builder()
                .routineId(groupRoutine.getId())
                .groupId(groupRoutine.getGroup().getId())
                .categoryId(groupRoutine.getCategory().getId())
                .categoryName(groupRoutine.getCategory().getName())
                .title(groupRoutine.getTitle())
                .description(groupRoutine.getDescription())
                .schedules(schedules)
                .assignmentCount(assignmentCount)
                .build();
    }

    /**
     * 그룹 루틴 일정을 응답용 일정으로 변환한다.
     *
     * @param schedule 그룹 루틴 일정
     * @return 요일과 시간 범위를 담은 응답 일정
     */
    private static GroupResDTO.RoutineSchedule toRoutineSchedule(GroupRoutineSchedule schedule) {
        return GroupResDTO.RoutineSchedule.builder()
                .repeatDay(schedule.getRepeatDay())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .build();
    }

    /**
     * 오늘의 그룹 루틴 조회 Projection 목록을 API 응답으로 변환한다.
     *
     * @param projections Repository가 조회한 오늘의 할당 목록
     * @return 오늘의 그룹 루틴 목록 응답
     */
    public static GroupResDTO.TodayRoutineList toTodayRoutineList(
            List<TodayAssignmentProjection> projections
    ) {
        List<GroupResDTO.TodayRoutine> routines = projections.stream()
                .map(GroupConverter::toTodayRoutine)
                .toList();

        return GroupResDTO.TodayRoutineList.builder()
                .routines(routines)
                .build();
    }

    /** Repository 조회 Projection 한 건을 오늘의 그룹 루틴 응답으로 변환한다. */
    private static GroupResDTO.TodayRoutine toTodayRoutine(TodayAssignmentProjection projection) {
        return GroupResDTO.TodayRoutine.builder()
                .assignmentId(projection.assignmentId())
                .routineId(projection.routineId())
                .groupId(projection.groupId())
                .groupName(projection.groupName())
                .categoryId(projection.categoryId())
                .categoryName(projection.categoryName())
                .title(projection.title())
                .description(projection.description())
                .assignedDate(projection.assignedDate())
                .scheduledStartTime(projection.scheduledStartTime())
                .scheduledEndTime(projection.scheduledEndTime())
                .status(projection.status())
                .build();
    }
}
