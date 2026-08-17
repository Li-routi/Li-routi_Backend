package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineSchedule;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.group.repository.GroupRoutineScheduleRepository;
import com.lirouti.domain.member.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupRoutineAssignmentCommandService 테스트")
class GroupRoutineAssignmentCommandServiceTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private GroupRoutineAssignmentRepository assignmentRepository;
    @Mock
    private GroupRoutineRepository groupRoutineRepository;
    @Mock
    private GroupRoutineScheduleRepository scheduleRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private GroupMemberActivityCommandService groupMemberActivityCommandService;
    @Mock
    private GroupRoutineAssignmentStatusRefreshBatchService statusRefreshBatchService;
    @Mock
    private Clock clock;
    @Mock
    private GroupRoutine groupRoutine;
    @Mock
    private GroupRoutineSchedule schedule;
    @Mock
    private GroupRoutine secondRoutine;
    @Mock
    private GroupRoutineSchedule secondSchedule;
    @Mock
    private Group group;
    @Mock
    private GroupMember groupMember;
    @Mock
    private Member member;
    @Mock
    private com.lirouti.domain.group.entity.GroupRoutineAssignment assignment;

    @InjectMocks
    private GroupRoutineAssignmentCommandService assignmentCommandService;

    @Test
    @DisplayName("그룹 탈퇴 회원의 미완료 할당만 제거한다")
    void deleteUnfinishedAssignmentsForLeaver_DeletesOnlyUnfinishedAssignments() {
        // given
        when(assignmentRepository.deleteUnfinishedAssignmentsForLeaver(
                10L,
                1L,
                List.of(
                        GroupRoutineAssignmentStatus.PENDING,
                        GroupRoutineAssignmentStatus.IN_PROGRESS
                )
        )).thenReturn(2);

        // when
        int result = assignmentCommandService.deleteUnfinishedAssignmentsForLeaver(10L, 1L);

        // then
        assertThat(result).isEqualTo(2);
        verify(assignmentRepository).deleteUnfinishedAssignmentsForLeaver(
                10L,
                1L,
                List.of(
                        GroupRoutineAssignmentStatus.PENDING,
                        GroupRoutineAssignmentStatus.IN_PROGRESS
                )
        );
    }

    @Test
    @DisplayName("루틴 삭제 시 PENDING과 IN_PROGRESS 할당만 일괄 삭제한다")
    void deleteMutableAssignments_Routine_DeletesOnlyMutableStatuses() {
        // given
        when(assignmentRepository.deleteAllByGroupRoutineIdAndStatusIn(
                100L,
                List.of(
                        GroupRoutineAssignmentStatus.PENDING,
                        GroupRoutineAssignmentStatus.IN_PROGRESS
                )
        )).thenReturn(4);

        // when
        int result = assignmentCommandService.deleteMutableAssignments(100L);

        // then
        assertThat(result).isEqualTo(4);
        verify(assignmentRepository).deleteAllByGroupRoutineIdAndStatusIn(
                100L,
                List.of(
                        GroupRoutineAssignmentStatus.PENDING,
                        GroupRoutineAssignmentStatus.IN_PROGRESS
                )
        );
    }

    @Test
    @DisplayName("루틴 생성일이 반복 요일이면 ACTIVE 구성원에게 시간 스냅샷을 할당한다")
    void assignRoutineToActiveMembersToday_MatchingDay_CreatesSnapshot() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 7, 23, 8, 0);
        givenNow(now);
        LocalDate today = now.toLocalDate();
        givenRoutine(today.getDayOfWeek());
        when(groupMemberRepository.findAllByGroupIdAndStatus(10L, GroupMemberStatus.ACTIVE))
                .thenReturn(List.of(groupMember));
        when(groupMember.getMember()).thenReturn(member);
        when(member.getId()).thenReturn(1L);
        givenInsertedAssignment();

        // when
        int result = assignmentCommandService.assignRoutineToActiveMembersToday(groupRoutine);

        // then
        assertThat(result).isEqualTo(1);
        verify(assignmentRepository).insertIfAbsent(
                100L,
                1L,
                today,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                GroupRoutineAssignmentStatus.PENDING.name()
        );
    }

    @Test
    @DisplayName("루틴 생성일이 반복 요일이 아니면 당일 할당을 만들지 않는다")
    void assignRoutineToActiveMembersToday_NonMatchingDay_DoesNotAssign() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 7, 23, 8, 0);
        givenNow(now);
        DayOfWeek tomorrow = now.toLocalDate().plusDays(1).getDayOfWeek();
        when(groupRoutine.getSchedules()).thenReturn(List.of(schedule));
        when(schedule.getRepeatDay()).thenReturn(tomorrow);

        // when
        int result = assignmentCommandService.assignRoutineToActiveMembersToday(groupRoutine);

        // then
        assertThat(result).isZero();
        verify(groupMemberRepository, never()).findAllByGroupIdAndStatus(10L, GroupMemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("가입 기준 시각이 수행 시간 안이면 진행 중 상태로 오늘 반복 루틴을 할당한다")
    void assignTodayRoutinesToMember_InProgress_AssignsTodaySchedules() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 7, 23, 9, 30);
        LocalDate today = now.toLocalDate();
        when(scheduleRepository.findAllWithRoutineByGroupIdAndRepeatDay(
                10L,
                today.getDayOfWeek()
        )).thenReturn(List.of(schedule));
        when(schedule.getGroupRoutine()).thenReturn(groupRoutine);
        when(groupRoutine.getId()).thenReturn(100L);
        when(schedule.getStartTime()).thenReturn(LocalTime.of(9, 0));
        when(schedule.getEndTime()).thenReturn(LocalTime.of(10, 0));
        when(groupRoutineRepository.findActiveByIdForUpdate(100L)).thenReturn(Optional.of(groupRoutine));
        givenInsertedAssignment();

        // when
        assignmentCommandService.assignTodayRoutinesToMember(10L, 1L, now);

        // then
        verify(assignmentRepository).insertIfAbsent(
                100L,
                1L,
                today,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                GroupRoutineAssignmentStatus.IN_PROGRESS.name()
        );
    }

    @Test
    @DisplayName("가입 기준 시각이 시작 전이면 PENDING으로 할당하고 종료 시각 이상이면 생성하지 않는다")
    void assignTodayRoutinesToMember_PendingAndExpired_AssignsOnlyPerformableSchedules() {
        // given
        LocalDateTime joinedAt = LocalDateTime.of(2026, 7, 23, 9, 0);
        LocalDate today = joinedAt.toLocalDate();
        when(scheduleRepository.findAllWithRoutineByGroupIdAndRepeatDay(10L, today.getDayOfWeek()))
                .thenReturn(List.of(schedule, secondSchedule));
        when(schedule.getGroupRoutine()).thenReturn(groupRoutine);
        when(groupRoutine.getId()).thenReturn(100L);
        when(schedule.getStartTime()).thenReturn(LocalTime.of(10, 0));
        when(schedule.getEndTime()).thenReturn(LocalTime.of(11, 0));
        lenient().when(secondSchedule.getGroupRoutine()).thenReturn(secondRoutine);
        lenient().when(secondRoutine.getId()).thenReturn(101L);
        when(secondSchedule.getEndTime()).thenReturn(LocalTime.of(9, 0));
        when(groupRoutineRepository.findActiveByIdForUpdate(100L)).thenReturn(Optional.of(groupRoutine));
        givenInsertedAssignment();

        // when
        assignmentCommandService.assignTodayRoutinesToMember(10L, 1L, joinedAt);

        // then
        verify(assignmentRepository, times(1)).insertIfAbsent(
                100L, 1L, today, LocalTime.of(10, 0), LocalTime.of(11, 0),
                GroupRoutineAssignmentStatus.PENDING.name());
        verify(groupRoutineRepository, times(1)).findActiveByIdForUpdate(100L);
        verify(groupRoutineRepository, never()).findActiveByIdForUpdate(101L);
        verify(assignmentRepository, times(1)).insertIfAbsent(
                anyLong(), anyLong(), any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("일일 할당은 오늘 요일의 루틴을 조회하고 그룹별 ACTIVE 구성원을 재사용한다")
    void assignScheduledRoutinesForDate_TodaySchedules_AssignsAllIdempotently() {
        // given
        LocalDate assignedDate = LocalDate.of(2026, 7, 23);
        givenNow(LocalDateTime.of(2026, 7, 23, 18, 30));
        when(scheduleRepository.findAllWithRoutineAndGroupByRepeatDay(DayOfWeek.THURSDAY))
                .thenReturn(List.of(schedule, secondSchedule));
        when(schedule.getGroupRoutine()).thenReturn(groupRoutine);
        when(secondSchedule.getGroupRoutine()).thenReturn(secondRoutine);
        when(groupRoutine.getGroup()).thenReturn(group);
        when(secondRoutine.getGroup()).thenReturn(group);
        when(group.getId()).thenReturn(10L);
        when(groupRoutine.getId()).thenReturn(100L);
        when(secondRoutine.getId()).thenReturn(101L);
        when(schedule.getStartTime()).thenReturn(LocalTime.of(9, 0));
        when(schedule.getEndTime()).thenReturn(LocalTime.of(10, 0));
        when(secondSchedule.getStartTime()).thenReturn(LocalTime.of(18, 0));
        when(secondSchedule.getEndTime()).thenReturn(LocalTime.of(19, 0));
        when(groupMemberRepository.findAllByGroupIdAndStatus(10L, GroupMemberStatus.ACTIVE))
                .thenReturn(List.of(groupMember));
        when(groupMember.getMember()).thenReturn(member);
        when(member.getId()).thenReturn(1L);
        when(groupRoutineRepository.findActiveByIdForUpdate(100L)).thenReturn(Optional.of(groupRoutine));
        when(groupRoutineRepository.findActiveByIdForUpdate(101L)).thenReturn(Optional.of(secondRoutine));
        givenInsertedAssignment();

        // when
        int result = assignmentCommandService.assignScheduledRoutinesForDate(assignedDate);

        // then
        assertThat(result).isEqualTo(2);
        verify(groupMemberRepository, times(1))
                .findAllByGroupIdAndStatus(10L, GroupMemberStatus.ACTIVE);
        verify(assignmentRepository).insertIfAbsent(
                100L,
                1L,
                assignedDate,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                GroupRoutineAssignmentStatus.MISSED.name()
        );
        verify(assignmentRepository).insertIfAbsent(
                101L,
                1L,
                assignedDate,
                LocalTime.of(18, 0),
                LocalTime.of(19, 0),
                GroupRoutineAssignmentStatus.IN_PROGRESS.name()
        );
    }

    @Test
    @DisplayName("삭제로 비활성화된 루틴은 일일 할당 생성에서 제외한다")
    void assignScheduledRoutinesForDate_InactiveRoutineAfterLock_DoesNotAssign() {
        // given
        LocalDate assignedDate = LocalDate.of(2026, 7, 23);
        when(scheduleRepository.findAllWithRoutineAndGroupByRepeatDay(DayOfWeek.THURSDAY))
                .thenReturn(List.of(schedule));
        when(schedule.getGroupRoutine()).thenReturn(groupRoutine);
        when(groupRoutine.getId()).thenReturn(100L);
        when(groupRoutineRepository.findActiveByIdForUpdate(100L)).thenReturn(Optional.empty());

        // when
        int result = assignmentCommandService.assignScheduledRoutinesForDate(assignedDate);

        // then
        assertThat(result).isZero();
        verify(groupMemberRepository, never())
                .findAllByGroupIdAndStatus(any(), any());
        verify(assignmentRepository, never()).insertIfAbsent(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("이미 존재하는 당일 할당은 생성 건수에 포함하지 않는다")
    void assignRoutineToActiveMembersToday_DuplicateAssignment_ReturnsZero() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 7, 23, 8, 0);
        givenNow(now);
        givenRoutine(now.getDayOfWeek());
        when(groupMemberRepository.findAllByGroupIdAndStatus(10L, GroupMemberStatus.ACTIVE))
                .thenReturn(List.of(groupMember));
        when(groupMember.getMember()).thenReturn(member);
        when(member.getId()).thenReturn(1L);
        when(assignmentRepository.insertIfAbsent(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(0);

        // when
        int result = assignmentCommandService.assignRoutineToActiveMembersToday(groupRoutine);

        // then
        assertThat(result).isZero();
    }

    @Test
    @DisplayName("수행 시간 안의 미완료 할당은 조건부 업데이트로 COMPLETED 처리한다")
    void completeAssignment_WithinSchedule_CompletesAtomically() {
        // given
        LocalDateTime verifiedAt = LocalDateTime.of(2026, 7, 23, 9, 30);
        when(assignmentRepository.markCompletedIfInProgress(
                200L,
                verifiedAt.toLocalDate(),
                verifiedAt.toLocalTime(),
                List.of(
                        GroupRoutineAssignmentStatus.PENDING,
                        GroupRoutineAssignmentStatus.IN_PROGRESS
                ),
                GroupRoutineAssignmentStatus.COMPLETED
        )).thenReturn(1);

        // when
        assignmentCommandService.completeAssignment(200L, verifiedAt);

        // then
        verify(assignmentRepository).markCompletedIfInProgress(
                200L,
                verifiedAt.toLocalDate(),
                verifiedAt.toLocalTime(),
                List.of(
                        GroupRoutineAssignmentStatus.PENDING,
                        GroupRoutineAssignmentStatus.IN_PROGRESS
                ),
                GroupRoutineAssignmentStatus.COMPLETED
        );
        verify(assignmentRepository, never()).findById(200L);
    }

    @Test
    @DisplayName("마감 후 인증은 MISSED 할당을 COMPLETED로 변경하지 않고 거절한다")
    void completeAssignment_AfterDeadline_ThrowsNotInProgress() {
        // given
        LocalDateTime verifiedAt = LocalDateTime.of(2026, 7, 23, 10, 0);
        when(assignmentRepository.findById(200L)).thenReturn(Optional.of(assignment));
        when(assignment.getStatus()).thenReturn(GroupRoutineAssignmentStatus.MISSED);

        // when & then
        assertThatThrownBy(() -> assignmentCommandService.completeAssignment(200L, verifiedAt))
                .isInstanceOfSatisfying(GroupException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(GroupErrorCode.GROUP_ROUTINE_ASSIGNMENT_NOT_IN_PROGRESS)
                );
    }

    @Test
    @DisplayName("상태 갱신은 마감 처리를 먼저 하고 시작 처리를 수행한다")
    void refreshAssignmentStatuses_ExpiresBeforeStarting() {
        // given
        LocalDateTime currentDateTime = LocalDateTime.of(2026, 7, 23, 10, 0);

        when(statusRefreshBatchService.resolveExpiredAssignments(currentDateTime, 100))
                .thenReturn(0);

        // when
        assignmentCommandService.refreshAssignmentStatuses(currentDateTime);

        // then
        InOrder inOrder = inOrder(statusRefreshBatchService);
        inOrder.verify(statusRefreshBatchService)
                .resolveExpiredAssignments(currentDateTime, 100);
        inOrder.verify(statusRefreshBatchService)
                .markStartedAssignmentsInProgress(currentDateTime);
    }

    @Test
    @DisplayName("마감 처리는 남은 대상이 없을 때까지 batch 단위로 반복한다")
    void refreshAssignmentStatuses_RepeatsUntilNoMissedAssignmentsRemain() {
        // given
        LocalDateTime currentDateTime = LocalDateTime.of(2026, 7, 23, 10, 0);
        when(statusRefreshBatchService.resolveExpiredAssignments(currentDateTime, 100))
                .thenReturn(100, 40, 0);

        // when
        assignmentCommandService.refreshAssignmentStatuses(currentDateTime);

        // then
        InOrder inOrder = inOrder(statusRefreshBatchService);
        inOrder.verify(statusRefreshBatchService, times(3))
                .resolveExpiredAssignments(currentDateTime, 100);
        inOrder.verify(statusRefreshBatchService)
                .markStartedAssignmentsInProgress(currentDateTime);
    }

    @Test
    @DisplayName("오늘 일정 수정은 미확정 할당만 갱신하고 확정 이력을 보존하며 누락 할당을 생성한다")
    void synchronizeRoutineAssignmentsToday_TodaySchedule_ReconcilesActiveMembers() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 7, 23, 9, 30);
        givenNow(now);
        givenRoutine(now.toLocalDate().getDayOfWeek());

        GroupMember completedMember = mock(GroupMember.class);
        GroupMember missedMember = mock(GroupMember.class);
        GroupMember missingMember = mock(GroupMember.class);
        Member completedUser = mock(Member.class);
        Member missedUser = mock(Member.class);
        Member missingUser = mock(Member.class);
        when(groupMemberRepository.findAllByGroupIdAndStatus(10L, GroupMemberStatus.ACTIVE))
                .thenReturn(List.of(groupMember, completedMember, missedMember, missingMember));
        when(groupMember.getMember()).thenReturn(member);
        when(member.getId()).thenReturn(1L);
        when(completedMember.getMember()).thenReturn(completedUser);
        when(completedUser.getId()).thenReturn(2L);
        when(missedMember.getMember()).thenReturn(missedUser);
        when(missedUser.getId()).thenReturn(3L);
        when(missingMember.getMember()).thenReturn(missingUser);
        when(missingUser.getId()).thenReturn(4L);

        com.lirouti.domain.group.entity.GroupRoutineAssignment pending = assignment(
                201L, member, GroupRoutineAssignmentStatus.PENDING
        );
        com.lirouti.domain.group.entity.GroupRoutineAssignment completed = assignment(
                202L, completedUser, GroupRoutineAssignmentStatus.COMPLETED
        );
        com.lirouti.domain.group.entity.GroupRoutineAssignment missed = assignment(
                203L, missedUser, GroupRoutineAssignmentStatus.MISSED
        );
        when(assignmentRepository.findAllByGroupRoutineIdAndAssignedDateForUpdate(
                100L, now.toLocalDate()
        )).thenReturn(List.of(pending, completed, missed));

        // when
        int result = assignmentCommandService.synchronizeRoutineAssignmentsToday(groupRoutine);

        // then
        assertThat(result).isEqualTo(4);
        verify(assignmentRepository).rescheduleAssignmentsIfMutable(
                List.of(201L),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                GroupRoutineAssignmentStatus.IN_PROGRESS,
                List.of(
                        GroupRoutineAssignmentStatus.PENDING,
                        GroupRoutineAssignmentStatus.IN_PROGRESS
                )
        );
        verify(assignmentRepository).insertIfAbsent(
                100L,
                4L,
                now.toLocalDate(),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                GroupRoutineAssignmentStatus.IN_PROGRESS.name()
        );
        verify(assignmentRepository, never()).deleteAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("오늘 요일이 제거되면 미확정 할당만 삭제하고 완료·미이행 이력을 보존한다")
    void synchronizeRoutineAssignmentsToday_RemovedToday_DeletesOnlyMutableAssignments() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 7, 23, 9, 30);
        givenNow(now);
        DayOfWeek anotherDay = now.toLocalDate().plusDays(1).getDayOfWeek();
        when(groupRoutine.getSchedules()).thenReturn(List.of(schedule));
        when(schedule.getRepeatDay()).thenReturn(anotherDay);
        when(groupRoutine.getId()).thenReturn(100L);
        when(groupRoutine.getGroup()).thenReturn(group);
        when(group.getId()).thenReturn(10L);

        GroupMember completedMember = mock(GroupMember.class);
        GroupMember missedMember = mock(GroupMember.class);
        Member completedUser = mock(Member.class);
        Member missedUser = mock(Member.class);
        when(groupMemberRepository.findAllByGroupIdAndStatus(10L, GroupMemberStatus.ACTIVE))
                .thenReturn(List.of(groupMember, completedMember, missedMember));
        when(groupMember.getMember()).thenReturn(member);
        when(member.getId()).thenReturn(1L);
        when(completedMember.getMember()).thenReturn(completedUser);
        when(completedUser.getId()).thenReturn(2L);
        when(missedMember.getMember()).thenReturn(missedUser);
        when(missedUser.getId()).thenReturn(3L);

        com.lirouti.domain.group.entity.GroupRoutineAssignment pending = assignment(
                201L, member, GroupRoutineAssignmentStatus.PENDING
        );
        com.lirouti.domain.group.entity.GroupRoutineAssignment completed = assignment(
                202L, completedUser, GroupRoutineAssignmentStatus.COMPLETED
        );
        com.lirouti.domain.group.entity.GroupRoutineAssignment missed = assignment(
                203L, missedUser, GroupRoutineAssignmentStatus.MISSED
        );
        when(assignmentRepository.findAllByGroupRoutineIdAndAssignedDateForUpdate(
                100L, now.toLocalDate()
        )).thenReturn(List.of(pending, completed, missed));

        // when
        int result = assignmentCommandService.synchronizeRoutineAssignmentsToday(groupRoutine);

        // then
        assertThat(result).isEqualTo(2);
        verify(assignmentRepository).deleteAll(List.of(pending));
        verify(assignmentRepository).flush();
        verify(assignmentRepository, never()).rescheduleAssignmentsIfMutable(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList()
        );
        verify(assignmentRepository, never()).insertIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private void givenRoutine(DayOfWeek repeatDay) {
        when(groupRoutine.getSchedules()).thenReturn(List.of(schedule));
        when(schedule.getRepeatDay()).thenReturn(repeatDay);
        when(schedule.getGroupRoutine()).thenReturn(groupRoutine);
        when(groupRoutine.getId()).thenReturn(100L);
        when(groupRoutine.getGroup()).thenReturn(group);
        when(group.getId()).thenReturn(10L);
        when(schedule.getStartTime()).thenReturn(LocalTime.of(9, 0));
        when(schedule.getEndTime()).thenReturn(LocalTime.of(10, 0));
    }

    private void givenNow(LocalDateTime now) {
        when(clock.instant()).thenReturn(now.atZone(KST).toInstant());
        when(clock.getZone()).thenReturn(KST);
    }

    private void givenInsertedAssignment() {
        when(assignmentRepository.insertIfAbsent(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(1);
    }

    private com.lirouti.domain.group.entity.GroupRoutineAssignment assignment(
            Long id,
            Member assignedMember,
            GroupRoutineAssignmentStatus status
    ) {
        com.lirouti.domain.group.entity.GroupRoutineAssignment target =
                com.lirouti.domain.group.entity.GroupRoutineAssignment.builder()
                        .groupRoutine(groupRoutine)
                        .member(assignedMember)
                        .assignedDate(LocalDate.of(2026, 7, 23))
                        .scheduledStartTime(LocalTime.of(9, 0))
                        .scheduledEndTime(LocalTime.of(10, 0))
                        .status(status)
                        .build();
        ReflectionTestUtils.setField(target, "id", id);
        return target;
    }
}
