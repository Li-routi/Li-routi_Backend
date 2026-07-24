package com.lirouti.domain.group.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.Builder;

public final class GroupResDTO {
    private GroupResDTO() {
    }

    /**
     * 그룹 루틴 생성 결과와 생성 당일 할당 대상 수를 전달한다.
     *
     * @param routineId 생성된 그룹 루틴 ID
     * @param groupId 루틴이 속한 그룹 ID
     * @param categoryId 루틴 카테고리 ID
     * @param categoryName 루틴 카테고리 이름
     * @param title 루틴 제목
     * @param description 루틴 설명
     * @param schedules 요일 순서로 정렬된 반복 일정
     * @param assignmentCount 생성 당일 할당 대상 수
     */
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

    /**
     * 그룹 루틴의 요일별 수행 시간 범위를 전달한다.
     *
     * @param repeatDay 반복 요일
     * @param startTime 수행 시작 시각
     * @param endTime 수행 마감 시각
     */
    @Builder
    public record RoutineSchedule(
            DayOfWeek repeatDay,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime
    ) {
    }

    /** 오늘 로그인 회원에게 할당된 그룹 루틴 목록을 전달한다. */
    @Builder
    public record TodayRoutineList(
            List<TodayRoutine> routines
    ) {
    }

    /**
     * 오늘의 그룹 루틴 할당과 소속 그룹 및 카테고리 정보를 전달한다.
     * 수행 시간은 할당 생성 시점에 저장한 스냅샷이다.
     */
    @Builder
    public record TodayRoutine(
            Long assignmentId,
            Long routineId,
            Long groupId,
            String groupName,
            Long categoryId,
            String categoryName,
            String title,
            String description,
            LocalDate assignedDate,
            @JsonFormat(pattern = "HH:mm") LocalTime scheduledStartTime,
            @JsonFormat(pattern = "HH:mm") LocalTime scheduledEndTime,
            GroupRoutineAssignmentStatus status
    ) {
    }
}
