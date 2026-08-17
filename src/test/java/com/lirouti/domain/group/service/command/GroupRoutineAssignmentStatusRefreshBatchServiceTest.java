package com.lirouti.domain.group.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.achievement.event.GroupAchievementProgressEvent;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.enums.GroupStatus;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationLikeRepository;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;

@ExtendWith(MockitoExtension.class)
@DisplayName("그룹 루틴 마감 batch 서비스 테스트")
class GroupRoutineAssignmentStatusRefreshBatchServiceTest {
    @Mock private GroupRepository groupRepository;
    @Mock private GroupRoutineAssignmentRepository assignmentRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private GroupRoutineVerificationLikeRepository likeRepository;
    @Mock private GroupMemberActivityCommandService activityCommandService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private GroupRoutineAssignmentStatusRefreshBatchService service;

    @Test
    @DisplayName("잠긴 그룹의 마감 Assignment를 bulk 처리하고 같은 batch에서 스트릭을 초기화한다")
    void resolveExpiredAssignments_ResetsStreakInSameBatch() {
        LocalDateTime currentDateTime = LocalDateTime.of(2026, 8, 7, 10, 0);
        List<GroupRoutineAssignmentStatus> unfinishedStatuses = List.of(
                GroupRoutineAssignmentStatus.PENDING,
                GroupRoutineAssignmentStatus.IN_PROGRESS
        );
        Group group = mock(Group.class);
        GroupRoutineAssignment first = mock(GroupRoutineAssignment.class);
        GroupRoutineAssignment second = mock(GroupRoutineAssignment.class);
        GroupRoutine routine = mock(GroupRoutine.class);
        Member member = mock(Member.class);
        when(group.getId()).thenReturn(1L);
        when(routine.getGroup()).thenReturn(group);
        when(member.getId()).thenReturn(2L);
        when(first.getGroupRoutine()).thenReturn(routine);
        when(second.getGroupRoutine()).thenReturn(routine);
        when(first.getMember()).thenReturn(member);
        when(second.getMember()).thenReturn(member);
        when(first.getAssignedDate()).thenReturn(LocalDate.of(2026, 8, 7));
        when(second.getAssignedDate()).thenReturn(LocalDate.of(2026, 8, 7));
        when(first.getId()).thenReturn(10L);
        when(second.getId()).thenReturn(11L);
        when(groupRepository.findExpiredAssignmentGroupsForUpdate(any(), any(), any(), any(), any()))
                .thenReturn(List.of(group));
        when(assignmentRepository.findExpiredAssignmentsByGroupIdsForUpdate(any(), any(), any(), any()))
                .thenReturn(List.of(first, second));
        when(assignmentRepository.markAssignmentsMissedByIds(any(), any(), any())).thenReturn(2);
        when(groupMemberRepository.countActiveMembersByGroupId(1L, com.lirouti.domain.group.enums.GroupMemberStatus.ACTIVE))
                .thenReturn(3L);
        when(likeRepository.countByVerificationIds(any())).thenReturn(java.util.Map.of());

        int result = service.resolveExpiredAssignments(currentDateTime, 100);

        assertThat(result).isEqualTo(2);
        verify(groupRepository).findExpiredAssignmentGroupsForUpdate(
                eq(GroupStatus.ACTIVE),
                eq(LocalDate.of(2026, 8, 7)),
                eq(LocalTime.of(10, 0)),
                eq(unfinishedStatuses),
                eq(PageRequest.of(0, 100))
        );
        verify(assignmentRepository).findExpiredAssignmentsByGroupIdsForUpdate(
                eq(List.of(1L)),
                eq(LocalDate.of(2026, 8, 7)),
                eq(LocalTime.of(10, 0)),
                eq(unfinishedStatuses)
        );
        verify(activityCommandService).resetCurrentStreaksForMissedAssignments(List.of(10L, 11L));
    }

    @ParameterizedTest(name = "ACTIVE {0}명, 인증={1}, Like={2}개면 {3}")
    @MethodSource("deadlineDecisions")
    @DisplayName("마감 시 인증 여부·ACTIVE 인원·Like 경계값으로 최종 상태를 판정한다")
    void markExpiredAssignments_DecidesByVerificationMemberCountAndLikes(
            long activeMemberCount,
            boolean hasVerification,
            long likeCount,
            GroupRoutineAssignmentStatus expectedStatus
    ) {
        DeadlineFixture fixture = deadlineFixture(hasVerification);
        when(groupMemberRepository.countActiveMembersByGroupId(1L,
                com.lirouti.domain.group.enums.GroupMemberStatus.ACTIVE)).thenReturn(activeMemberCount);
        when(likeRepository.countByVerificationIds(hasVerification ? List.of(100L) : List.of()))
                .thenReturn(likeCount == 0 ? Map.of() : Map.of(100L, likeCount));
        if (expectedStatus == GroupRoutineAssignmentStatus.COMPLETED) {
            when(assignmentRepository.markAssignmentsCompletedByIds(any(), any(), any())).thenReturn(1);
        } else {
            when(assignmentRepository.markAssignmentsMissedByIds(any(), any(), any())).thenReturn(1);
        }

        int result = service.resolveExpiredAssignments(LocalDateTime.of(2026, 8, 7, 10, 0), 100);

        assertThat(result).isEqualTo(1);
        if (expectedStatus == GroupRoutineAssignmentStatus.COMPLETED) {
            verify(assignmentRepository).markAssignmentsCompletedByIds(
                    List.of(fixture.assignmentId()), unfinishedStatuses(), expectedStatus);
            verify(activityCommandService).recordStreakIfAllAssignmentsCompleted(
                    1L, 2L, fixture.assignedDate());
            verify(eventPublisher).publishEvent(new GroupAchievementProgressEvent(
                    1L, "ACH-AC-009", 1, "GROUP_ASSIGNMENT_COMPLETE", fixture.assignmentId()));
        } else {
            verify(assignmentRepository).markAssignmentsMissedByIds(
                    List.of(fixture.assignmentId()), unfinishedStatuses(), expectedStatus);
            verify(activityCommandService).resetCurrentStreaksForMissedAssignments(
                    List.of(fixture.assignmentId()));
            verifyNoInteractions(eventPublisher);
        }
    }

    @Test
    @DisplayName("같은 날짜의 COMPLETED와 MISSED가 섞이면 완료 판정 뒤 MISSED 스트릭 초기화를 수행한다")
    void markExpiredAssignments_MixedCompletionAndMissed_RecordsThenResetsStreak() {
        DeadlineFixture completed = deadlineFixture(true, 10L);
        DeadlineFixture missed = deadlineFixture(false, 11L);
        when(assignmentRepository.findExpiredAssignmentsByGroupIdsForUpdate(any(), any(), any(), any()))
                .thenReturn(List.of(completed.assignment(), missed.assignment()));
        when(groupMemberRepository.countActiveMembersByGroupId(1L,
                com.lirouti.domain.group.enums.GroupMemberStatus.ACTIVE)).thenReturn(1L);
        when(likeRepository.countByVerificationIds(List.of(100L))).thenReturn(Map.of());
        when(assignmentRepository.markAssignmentsCompletedByIds(any(), any(), any())).thenReturn(1);
        when(assignmentRepository.markAssignmentsMissedByIds(any(), any(), any())).thenReturn(1);

        service.resolveExpiredAssignments(LocalDateTime.of(2026, 8, 7, 10, 0), 100);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(activityCommandService);
        order.verify(activityCommandService).recordStreakIfAllAssignmentsCompleted(1L, 2L, completed.assignedDate());
        order.verify(activityCommandService).resetCurrentStreaksForMissedAssignments(List.of(11L));
        verify(eventPublisher).publishEvent(new GroupAchievementProgressEvent(
                1L, "ACH-AC-009", 1, "GROUP_ASSIGNMENT_COMPLETE", 10L));
        verify(eventPublisher, never()).publishEvent(new GroupAchievementProgressEvent(
                1L, "ACH-SP-004", 1, "GROUP_ALL_COMPLETE_DAY",
                1L * 10_000_000L + completed.assignedDate().toEpochDay()));
    }

    @Test
    @DisplayName("같은 그룹·날짜의 완료 Assignment 여러 건은 AC-009은 건별로, SP-004는 한 번만 발행한다")
    void markExpiredAssignments_SameGroupAndDate_DeduplicatesAllMembersCompletedEvent() {
        DeadlineFixture first = deadlineFixture(true, 10L);
        DeadlineFixture second = deadlineFixture(true, 11L);
        when(assignmentRepository.findExpiredAssignmentsByGroupIdsForUpdate(any(), any(), any(), any()))
                .thenReturn(List.of(first.assignment(), second.assignment()));
        when(groupMemberRepository.countActiveMembersByGroupId(1L,
                com.lirouti.domain.group.enums.GroupMemberStatus.ACTIVE)).thenReturn(1L);
        when(likeRepository.countByVerificationIds(List.of(100L, 100L))).thenReturn(Map.of());
        when(assignmentRepository.markAssignmentsCompletedByIds(any(), any(), any())).thenReturn(2);
        when(activityCommandService.isAllMembersCompletedToday(1L, first.assignedDate())).thenReturn(true);

        int result = service.resolveExpiredAssignments(LocalDateTime.of(2026, 8, 7, 10, 0), 100);

        assertThat(result).isEqualTo(2);
        verify(activityCommandService, times(2))
                .recordStreakIfAllAssignmentsCompleted(1L, 2L, first.assignedDate());
        verify(eventPublisher).publishEvent(new GroupAchievementProgressEvent(
                1L, "ACH-AC-009", 1, "GROUP_ASSIGNMENT_COMPLETE", 10L));
        verify(eventPublisher).publishEvent(new GroupAchievementProgressEvent(
                1L, "ACH-AC-009", 1, "GROUP_ASSIGNMENT_COMPLETE", 11L));
        verify(activityCommandService, times(1))
                .isAllMembersCompletedToday(1L, first.assignedDate());
        verify(eventPublisher, times(1)).publishEvent(new GroupAchievementProgressEvent(
                1L, "ACH-SP-004", 1, "GROUP_ALL_COMPLETE_DAY",
                1L * 10_000_000L + first.assignedDate().toEpochDay()));
    }

    @Test
    @DisplayName("실제 MISSED 전이가 없으면 스트릭을 초기화하지 않는다")
    void resolveExpiredAssignments_NoTransition_DoesNotResetStreak() {
        Group group = mock(Group.class);
        GroupRoutineAssignment assignment = mock(GroupRoutineAssignment.class);
        GroupRoutine routine = mock(GroupRoutine.class);
        Member member = mock(Member.class);
        when(group.getId()).thenReturn(1L);
        when(routine.getGroup()).thenReturn(group);
        when(member.getId()).thenReturn(2L);
        when(assignment.getGroupRoutine()).thenReturn(routine);
        when(assignment.getMember()).thenReturn(member);
        when(assignment.getAssignedDate()).thenReturn(LocalDate.of(2026, 8, 7));
        when(assignment.getId()).thenReturn(10L);
        when(groupRepository.findExpiredAssignmentGroupsForUpdate(any(), any(), any(), any(), any()))
                .thenReturn(List.of(group));
        when(assignmentRepository.findExpiredAssignmentsByGroupIdsForUpdate(any(), any(), any(), any()))
                .thenReturn(List.of(assignment));
        when(assignmentRepository.markAssignmentsMissedByIds(any(), any(), any())).thenReturn(0);
        when(groupMemberRepository.countActiveMembersByGroupId(1L, com.lirouti.domain.group.enums.GroupMemberStatus.ACTIVE))
                .thenReturn(3L);
        when(likeRepository.countByVerificationIds(any())).thenReturn(java.util.Map.of());

        int result = service.resolveExpiredAssignments(
                LocalDateTime.of(2026, 8, 7, 10, 0), 100);

        assertThat(result).isZero();
        verifyNoInteractions(activityCommandService);
    }

    @Test
    @DisplayName("마감 대상 그룹이 없으면 Assignment와 활동 상태를 조회하지 않는다")
    void resolveExpiredAssignments_NoCandidate_ReturnsZero() {
        when(groupRepository.findExpiredAssignmentGroupsForUpdate(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        int result = service.resolveExpiredAssignments(
                LocalDateTime.of(2026, 8, 7, 10, 0), 100);

        assertThat(result).isZero();
        verifyNoInteractions(assignmentRepository, activityCommandService, eventPublisher);
    }

    private List<GroupRoutineAssignmentStatus> unfinishedStatuses() {
        return List.of(GroupRoutineAssignmentStatus.PENDING, GroupRoutineAssignmentStatus.IN_PROGRESS);
    }

    private static Stream<Arguments> deadlineDecisions() {
        return Stream.of(
                Arguments.of(1L, false, 0L, GroupRoutineAssignmentStatus.MISSED),
                Arguments.of(1L, true, 0L, GroupRoutineAssignmentStatus.COMPLETED),
                Arguments.of(2L, true, 0L, GroupRoutineAssignmentStatus.COMPLETED),
                Arguments.of(3L, true, 0L, GroupRoutineAssignmentStatus.MISSED),
                Arguments.of(3L, true, 1L, GroupRoutineAssignmentStatus.COMPLETED),
                Arguments.of(4L, true, 1L, GroupRoutineAssignmentStatus.MISSED),
                Arguments.of(4L, true, 2L, GroupRoutineAssignmentStatus.COMPLETED),
                Arguments.of(5L, true, 1L, GroupRoutineAssignmentStatus.MISSED),
                Arguments.of(5L, true, 2L, GroupRoutineAssignmentStatus.COMPLETED),
                Arguments.of(6L, true, 2L, GroupRoutineAssignmentStatus.MISSED),
                Arguments.of(6L, true, 3L, GroupRoutineAssignmentStatus.COMPLETED)
        );
    }

    private DeadlineFixture deadlineFixture(boolean hasVerification) {
        return deadlineFixture(hasVerification, 10L);
    }

    private DeadlineFixture deadlineFixture(boolean hasVerification, Long assignmentId) {
        Group group = mock(Group.class);
        GroupRoutine routine = mock(GroupRoutine.class);
        Member member = mock(Member.class);
        GroupRoutineAssignment assignment = mock(GroupRoutineAssignment.class);
        LocalDate assignedDate = LocalDate.of(2026, 8, 7);
        when(group.getId()).thenReturn(1L);
        when(routine.getGroup()).thenReturn(group);
        when(member.getId()).thenReturn(2L);
        when(assignment.getId()).thenReturn(assignmentId);
        when(assignment.getGroupRoutine()).thenReturn(routine);
        when(assignment.getMember()).thenReturn(member);
        when(assignment.getAssignedDate()).thenReturn(assignedDate);
        if (hasVerification) {
            GroupRoutineVerification verification = mock(GroupRoutineVerification.class);
            when(assignment.getVerification()).thenReturn(verification);
            when(verification.getId()).thenReturn(100L);
        }
        when(groupRepository.findExpiredAssignmentGroupsForUpdate(any(), any(), any(), any(), any()))
                .thenReturn(List.of(group));
        when(assignmentRepository.findExpiredAssignmentsByGroupIdsForUpdate(any(), any(), any(), any()))
                .thenReturn(List.of(assignment));
        return new DeadlineFixture(assignment, assignmentId, assignedDate);
    }

    private record DeadlineFixture(
            GroupRoutineAssignment assignment,
            Long assignmentId,
            LocalDate assignedDate
    ) {
    }
}
