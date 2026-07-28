package com.lirouti.domain.group.entity;

import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 그룹에 속하며 카테고리와 요일별 일정을 가지는 공동 루틴이다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "group_routine",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_group_routine_group_title",
                        columnNames = {"group_id", "title"}
                )
        }
)
public class GroupRoutine extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private RoutineCategory category;

    @Column(nullable = false, length = 20)
    private String title;

    @Column(nullable = false, length = 255)
    private String description;

    @OneToMany(mappedBy = "groupRoutine", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GroupRoutineSchedule> schedules = new ArrayList<>();

    /**
     * 검증된 그룹과 카테고리에 속하는 그룹 루틴을 생성한다.
     *
     * @param group 루틴이 속한 그룹
     * @param category 앱에서 관리하는 루틴 카테고리
     * @param title 그룹 내에서 구분되는 루틴 제목
     * @param description 루틴 설명
     */
    @Builder
    private GroupRoutine(Group group, RoutineCategory category, String title, String description) {
        this.group = group;
        this.category = category;
        this.title = title;
        this.description = description;
    }

    /**
     * 한 요일의 수행 시간 범위를 추가하고 루틴과 일정의 양방향 관계를 설정한다.
     *
     * @param repeatDay 반복 요일
     * @param startTime 수행 시작 시각
     * @param endTime 수행 마감 시각
     * @throws IllegalArgumentException 같은 요일의 일정이 이미 존재하는 경우
     */
    public void addSchedule(DayOfWeek repeatDay, LocalTime startTime, LocalTime endTime) {
        if (schedules.stream().anyMatch(schedule -> schedule.getRepeatDay() == repeatDay)) {
            throw new IllegalArgumentException("같은 요일의 일정을 중복해서 등록할 수 없습니다.");
        }
        schedules.add(GroupRoutineSchedule.builder()
                .groupRoutine(this)
                .repeatDay(repeatDay)
                .startTime(startTime)
                .endTime(endTime)
                .build());
    }

    /**
     * 검증된 카테고리와 루틴 기본 정보를 변경한다.
     * 루틴이 속한 그룹은 수정 대상이 아니므로 기존 연관관계를 유지한다.
     *
     * @param category 변경할 루틴 카테고리
     * @param title 변경할 루틴 제목
     * @param description 변경할 루틴 설명
     * @throws IllegalArgumentException 필수값이 없거나 길이 제약을 벗어난 경우
     */
    public void update(RoutineCategory category, String title, String description) {
        if (category == null) {
            throw new IllegalArgumentException("루틴 카테고리는 필수입니다.");
        }
        if (title == null || title.isBlank() || title.length() > 20) {
            throw new IllegalArgumentException("루틴 제목은 1자 이상 20자 이하여야 합니다.");
        }
        if (description == null || description.isBlank() || description.length() > 255) {
            throw new IllegalArgumentException("루틴 설명은 1자 이상 255자 이하여야 합니다.");
        }
        this.category = category;
        this.title = title;
        this.description = description;
    }

    /**
     * 전달된 요일별 일정으로 반복 규칙을 전체 동기화한다.
     * 같은 요일의 기존 일정은 시간만 변경하고, 빠진 요일은 제거하며, 새 요일은 추가한다.
     *
     * @param replacementSchedules 변경 후 유지할 전체 반복 일정
     * @throws IllegalArgumentException 일정이 비어 있거나 중복 요일·잘못된 시간 범위를 포함한 경우
     */
    public void replaceSchedules(List<ScheduleUpdate> replacementSchedules) {
        Set<DayOfWeek> replacementDays = validateReplacementSchedules(replacementSchedules);

        schedules.removeIf(schedule -> !replacementDays.contains(schedule.getRepeatDay()));
        replacementSchedules.forEach(replacement -> schedules.stream()
                .filter(schedule -> schedule.getRepeatDay() == replacement.repeatDay())
                .findFirst()
                .ifPresentOrElse(
                        schedule -> schedule.updateTimeRange(
                                replacement.startTime(),
                                replacement.endTime()
                        ),
                        () -> addSchedule(
                                replacement.repeatDay(),
                                replacement.startTime(),
                                replacement.endTime()
                        )
                ));
    }

    private Set<DayOfWeek> validateReplacementSchedules(
            List<ScheduleUpdate> replacementSchedules
    ) {
        if (replacementSchedules == null
                || replacementSchedules.isEmpty()
                || replacementSchedules.size() > 7) {
            throw new IllegalArgumentException("반복 일정은 1개 이상 7개 이하여야 합니다.");
        }

        Set<DayOfWeek> repeatDays = new HashSet<>();
        for (ScheduleUpdate schedule : replacementSchedules) {
            if (schedule == null || schedule.repeatDay() == null) {
                throw new IllegalArgumentException("반복 일정과 요일은 필수입니다.");
            }
            if (schedule.startTime() == null
                    || schedule.endTime() == null
                    || !schedule.startTime().isBefore(schedule.endTime())) {
                throw new IllegalArgumentException("시작 시간은 종료 시간보다 빨라야 합니다.");
            }
            if (!repeatDays.add(schedule.repeatDay())) {
                throw new IllegalArgumentException("같은 요일의 일정을 중복해서 등록할 수 없습니다.");
            }
        }
        return repeatDays;
    }

    /** 그룹 루틴의 한 요일에 적용할 변경 후 시간 범위다. */
    public record ScheduleUpdate(
            DayOfWeek repeatDay,
            LocalTime startTime,
            LocalTime endTime
    ) {
    }
}
