package com.lirouti.domain.group.entity;

import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 특정 날짜에 그룹 루틴을 회원에게 할당한 내역이다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "group_routine_assignment",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_group_routine_assignment_routine_member_date",
                        columnNames = {"group_routine_id", "member_id", "assigned_date"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_group_routine_assignment_member_date",
                        columnList = "member_id, assigned_date"
                ),
                @Index(
                        name = "idx_group_routine_assignment_status_date_end",
                        columnList = "status, assigned_date, scheduled_end_time"
                ),
                @Index(
                        name = "idx_group_routine_assignment_status_date_start",
                        columnList = "status, assigned_date, scheduled_start_time"
                )
        }
)
public class GroupRoutineAssignment extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_routine_id", nullable = false)
    private GroupRoutine groupRoutine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "assigned_date", nullable = false)
    private LocalDate assignedDate;

    @Column(name = "scheduled_start_time", nullable = false)
    private LocalTime scheduledStartTime;

    @Column(name = "scheduled_end_time", nullable = false)
    private LocalTime scheduledEndTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupRoutineAssignmentStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    @Builder
    private GroupRoutineAssignment(
            GroupRoutine groupRoutine,
            Member member,
            LocalDate assignedDate,
            LocalTime scheduledStartTime,
            LocalTime scheduledEndTime,
            GroupRoutineAssignmentStatus status
    ) {
        this.groupRoutine = groupRoutine;
        this.member = member;
        this.assignedDate = assignedDate;
        this.scheduledStartTime = scheduledStartTime;
        this.scheduledEndTime = scheduledEndTime;
        this.status = status;
    }
}
