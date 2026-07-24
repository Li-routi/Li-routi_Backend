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
import java.util.List;
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
}
