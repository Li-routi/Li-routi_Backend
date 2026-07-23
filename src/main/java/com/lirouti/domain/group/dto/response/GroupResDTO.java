package com.lirouti.domain.group.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import lombok.Builder;

public final class GroupResDTO {
    private GroupResDTO() {
    }

    @Builder
    public record RoutineCreateResult(
            Long routineId,
            Long groupId,
            Long categoryId,
            String categoryName,
            String title,
            String description,
            List<RoutineSchedule> schedules,
            int assignmentCount
    ) {
    }

    @Builder
    public record RoutineSchedule(
            DayOfWeek repeatDay,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime
    ) {
    }
}
