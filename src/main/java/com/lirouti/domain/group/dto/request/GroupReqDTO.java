package com.lirouti.domain.group.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public final class GroupReqDTO {
    private GroupReqDTO() {
    }

    public record CreateRoutine(
            @NotNull(message = "카테고리는 필수입니다.")
            @Positive(message = "카테고리 ID는 양수여야 합니다.")
            Long categoryId,

            @NotBlank(message = "루틴 제목은 필수입니다.")
            @Size(max = 20, message = "루틴 제목은 20자 이하여야 합니다.")
            String title,

            @NotBlank(message = "루틴 설명은 필수입니다.")
            @Size(max = 255, message = "루틴 설명은 255자 이하여야 합니다.")
            String description,

            @NotEmpty(message = "하나 이상의 반복 일정이 필요합니다.")
            @Size(max = 7, message = "반복 일정은 최대 7개까지 등록할 수 있습니다.")
            List<@NotNull(message = "반복 일정은 null일 수 없습니다.") @Valid RoutineSchedule> schedules
    ) {
        @AssertTrue(message = "같은 요일의 일정을 중복해서 등록할 수 없습니다.")
        @JsonIgnore
        public boolean isScheduleDayUnique() {
            if (schedules == null) {
                return true;
            }
            return schedules.stream()
                    .filter(Objects::nonNull)
                    .map(RoutineSchedule::repeatDay)
                    .filter(Objects::nonNull)
                    .allMatch(new HashSet<>()::add);
        }
    }

    public record RoutineSchedule(
            @NotNull(message = "반복 요일은 필수입니다.")
            DayOfWeek repeatDay,

            @NotNull(message = "시작 시간은 필수입니다.")
            @JsonFormat(pattern = "HH:mm")
            LocalTime startTime,

            @NotNull(message = "종료 시간은 필수입니다.")
            @JsonFormat(pattern = "HH:mm")
            LocalTime endTime
    ) {
        @AssertTrue(message = "시작 시간은 종료 시간보다 빨라야 합니다.")
        @JsonIgnore
        public boolean isTimeRangeValid() {
            if (startTime == null || endTime == null) {
                return true;
            }
            return startTime.isBefore(endTime);
        }
    }
}
