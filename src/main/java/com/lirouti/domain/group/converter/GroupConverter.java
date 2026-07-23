package com.lirouti.domain.group.converter;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineSchedule;
import com.lirouti.domain.group.entity.RoutineCategory;
import java.util.Comparator;
import java.util.List;

public final class GroupConverter {
    private GroupConverter() {
    }

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

    private static GroupResDTO.RoutineSchedule toRoutineSchedule(GroupRoutineSchedule schedule) {
        return GroupResDTO.RoutineSchedule.builder()
                .repeatDay(schedule.getRepeatDay())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .build();
    }
}
