package com.lirouti.domain.group.entity;

import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
}
