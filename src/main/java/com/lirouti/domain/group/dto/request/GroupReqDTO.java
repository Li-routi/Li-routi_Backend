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

    /**
     * 카테고리와 요일별 수행 일정을 포함한 그룹 루틴 생성 요청이다.
     *
     * @param categoryId 앱에서 관리하는 루틴 카테고리 ID
     * @param title 그룹 내 루틴 제목
     * @param description 루틴 설명
     * @param schedules 중복되지 않는 요일별 수행 일정
     */
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
        /**
         * null 요소와 null 요일은 각 필드 제약에 맡기고, 입력된 요일의 중복만 검증한다.
         *
         * @return null이 아닌 일정의 반복 요일이 모두 다르면 {@code true}
         */
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

    /**
     * 한 반복 요일에 적용할 수행 시간 범위다.
     *
     * @param repeatDay 반복 요일
     * @param startTime 수행 시작 시각
     * @param endTime 수행 마감 시각
     */
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
        /**
         * 시작·종료 값의 필수 검증과 분리해 시간의 선후 관계를 검증한다.
         *
         * @return 값이 누락됐거나 시작 시간이 종료 시간보다 빠르면 {@code true}
         */
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
