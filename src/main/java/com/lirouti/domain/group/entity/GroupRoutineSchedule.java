package com.lirouti.domain.group.entity;

import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** 그룹 루틴의 한 요일에 적용되는 당일 시간 범위다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "group_routine_schedule",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_group_routine_schedule_day",
                        columnNames = {"group_routine_id", "repeat_day"}
                )
        },
        check = {
                @CheckConstraint(
                        name = "ck_group_routine_schedule_time_range",
                        constraint = "start_time < end_time"
                )
        }
)
public class GroupRoutineSchedule extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_routine_id", nullable = false)
    private GroupRoutine groupRoutine;

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_day", nullable = false, length = 9)
    private DayOfWeek repeatDay;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /**
     * 그룹 루틴에 속하는 한 요일의 수행 시간 범위를 생성한다.
     *
     * @param groupRoutine 일정이 속한 그룹 루틴
     * @param repeatDay 반복 요일
     * @param startTime 수행 시작 시각
     * @param endTime 수행 마감 시각
     * @throws IllegalArgumentException 필수값이 없거나 시작 시각이 마감 시각보다 빠르지 않은 경우
     */
    @Builder
    private GroupRoutineSchedule(
            GroupRoutine groupRoutine,
            DayOfWeek repeatDay,
            LocalTime startTime,
            LocalTime endTime
    ) {
        if (groupRoutine == null || repeatDay == null) {
            throw new IllegalArgumentException("그룹 루틴과 반복 요일은 필수입니다.");
        }
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("시작 시간은 종료 시간보다 빨라야 합니다.");
        }
        this.groupRoutine = groupRoutine;
        this.repeatDay = repeatDay;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /**
     * 반복 요일은 유지하고 해당 요일의 수행 시간 범위를 변경한다.
     *
     * @param startTime 변경할 수행 시작 시각
     * @param endTime 변경할 수행 마감 시각
     * @throws IllegalArgumentException 시작·종료 시각이 없거나 유효한 선후 관계가 아닌 경우
     */
    public void updateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("시작 시간은 종료 시간보다 빨라야 합니다.");
        }
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
