package com.lirouti.domain.group.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.lirouti.domain.group.repository.GroupRoutineScheduleRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.member.entity.Member;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupRoutineAssignmentCommandService 테스트")
class GroupRoutineAssignmentCommandServiceTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private GroupRoutineAssignmentRepository assignmentRepository;
    @Mock
    private GroupRoutineScheduleRepository scheduleRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private GroupValidationService groupValidationService;
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
    @DisplayName("가입 당일에는 현재 시간과 관계없이 오늘 반복 루틴을 즉시 할당한다")
    void assignTodayRoutinesToMember_ActiveMember_AssignsTodaySchedules() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 7, 23, 9, 30);
        givenNow(now);
        LocalDate today = now.toLocalDate();
        when(groupValidationService.validateActiveGroupMember(10L, 1L)).thenReturn(groupMember);
        when(scheduleRepository.findAllWithRoutineByGroupIdAndRepeatDay(
                10L,
                today.getDayOfWeek()
        )).thenReturn(List.of(schedule));
        when(schedule.getGroupRoutine()).thenReturn(groupRoutine);
        when(groupRoutine.getId()).thenReturn(100L);
        when(schedule.getStartTime()).thenReturn(LocalTime.of(9, 0));
        when(schedule.getEndTime()).thenReturn(LocalTime.of(10, 0));
        when(groupMember.getMember()).thenReturn(member);
        when(member.getId()).thenReturn(1L);

        // when
        int result = assignmentCommandService.assignTodayRoutinesToMember(10L, 1L);

        // then
        assertThat(result).isEqualTo(1);
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

        // when
        assignmentCommandService.refreshAssignmentStatuses(currentDateTime);

        // then
        InOrder inOrder = inOrder(assignmentRepository);
        inOrder.verify(assignmentRepository).markExpiredAssignmentsMissed(
                currentDateTime.toLocalDate(),
                currentDateTime.toLocalTime(),
                List.of(
                        GroupRoutineAssignmentStatus.PENDING,
                        GroupRoutineAssignmentStatus.IN_PROGRESS
                ),
                GroupRoutineAssignmentStatus.MISSED
        );
        inOrder.verify(assignmentRepository).markStartedAssignmentsInProgress(
                currentDateTime.toLocalDate(),
                currentDateTime.toLocalTime(),
                GroupRoutineAssignmentStatus.PENDING,
                GroupRoutineAssignmentStatus.IN_PROGRESS
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
}
