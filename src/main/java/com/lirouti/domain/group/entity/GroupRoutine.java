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

    @Builder
    private GroupRoutine(Group group, RoutineCategory category, String title, String description) {
        this.group = group;
        this.category = category;
        this.title = title;
        this.description = description;
    }

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
