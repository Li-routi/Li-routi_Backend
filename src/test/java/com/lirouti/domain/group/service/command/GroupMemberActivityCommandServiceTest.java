package com.lirouti.domain.group.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.member.entity.Member;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupMember 활동 상태 명령 서비스 테스트")
class GroupMemberActivityCommandServiceTest {
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private GroupRoutineAssignmentRepository assignmentRepository;
    @InjectMocks private GroupMemberActivityCommandService service;

    @Test
    @DisplayName("당일 현재 가입 회차 Assignment가 모두 COMPLETED면 스트릭을 증가시킨다")
    void recordStreak_AllCompleted_IncreasesOnce() {
        GroupMember membership = membership();
        LocalDate assignedDate = LocalDate.of(2026, 8, 7);
        GroupRoutineAssignment completedAssignment = assignment(
                GroupRoutineAssignmentStatus.COMPLETED);
        when(groupMemberRepository.findByGroupIdAndMemberIdForUpdate(1L, 2L))
                .thenReturn(Optional.of(membership));
        when(assignmentRepository
                .findAllByGroupIdAndMemberIdAndAssignedDateAndCreatedAtAfterOrEqualForUpdate(
                        any(), any(), any(), any()))
                .thenReturn(List.of(completedAssignment));

        service.recordStreakIfAllAssignmentsCompleted(1L, 2L, assignedDate);
        service.recordStreakIfAllAssignmentsCompleted(1L, 2L, assignedDate);

        assertThat(membership.getCurrentStreak()).isEqualTo(1);
        assertThat(membership.getLongestStreak()).isEqualTo(1);
    }

    @Test
    @DisplayName("일부 Assignment가 완료되지 않았으면 스트릭을 증가시키지 않는다")
    void recordStreak_NotAllCompleted_DoesNotIncrease() {
        GroupMember membership = membership();
        GroupRoutineAssignment completedAssignment = assignment(
                GroupRoutineAssignmentStatus.COMPLETED);
        GroupRoutineAssignment inProgressAssignment = assignment(
                GroupRoutineAssignmentStatus.IN_PROGRESS);
        when(groupMemberRepository.findByGroupIdAndMemberIdForUpdate(1L, 2L))
                .thenReturn(Optional.of(membership));
        when(assignmentRepository
                .findAllByGroupIdAndMemberIdAndAssignedDateAndCreatedAtAfterOrEqualForUpdate(
                        any(), any(), any(), any()))
                .thenReturn(List.of(
                        completedAssignment,
                        inProgressAssignment
                ));

        service.recordStreakIfAllAssignmentsCompleted(1L, 2L, LocalDate.of(2026, 8, 7));

        assertThat(membership.getCurrentStreak()).isZero();
    }

    private GroupMember membership() {
        return GroupMember.builder()
                .member(mock(Member.class))
                .group(mock(Group.class))
                .role(GroupMemberRole.MEMBER)
                .build();
    }

    private GroupRoutineAssignment assignment(GroupRoutineAssignmentStatus status) {
        GroupRoutineAssignment assignment = mock(GroupRoutineAssignment.class);
        when(assignment.getStatus()).thenReturn(status);
        return assignment;
    }
}
