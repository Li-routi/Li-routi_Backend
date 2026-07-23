package com.lirouti.domain.group.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 그룹 루틴을 그룹 구성원에게 적용한 관계다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "group_routine_assignment",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_group_routine_assignment_routine_member",
                        columnNames = {"group_routine_id", "member_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_group_routine_assignment_member_routine",
                        columnList = "member_id, group_routine_id"
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

    @Builder
    private GroupRoutineAssignment(GroupRoutine groupRoutine, Member member) {
        this.groupRoutine = groupRoutine;
        this.member = member;
    }
}
