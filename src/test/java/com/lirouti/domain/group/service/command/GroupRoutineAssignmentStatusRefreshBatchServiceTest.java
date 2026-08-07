package com.lirouti.domain.group.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("그룹 루틴 마감 batch 서비스 테스트")
class GroupRoutineAssignmentStatusRefreshBatchServiceTest {
    @Mock private GroupRepository groupRepository;
    @Mock private GroupRoutineAssignmentRepository assignmentRepository;
    @Mock private GroupMemberActivityCommandService activityCommandService;
    @InjectMocks private GroupRoutineAssignmentStatusRefreshBatchService service;

    @Test
    @DisplayName("잠긴 그룹의 마감 Assignment를 bulk 처리하고 같은 batch에서 스트릭을 초기화한다")
    void markExpiredAssignmentsMissed_ResetsStreakInSameBatch() {
        Group group = mock(Group.class);
        GroupRoutineAssignment first = mock(GroupRoutineAssignment.class);
        GroupRoutineAssignment second = mock(GroupRoutineAssignment.class);
        when(group.getId()).thenReturn(1L);
        when(first.getId()).thenReturn(10L);
        when(second.getId()).thenReturn(11L);
        when(groupRepository.findExpiredAssignmentGroupsForUpdate(any(), any(), any(), any(), any()))
                .thenReturn(List.of(group));
        when(assignmentRepository.findExpiredAssignmentsByGroupIdsForUpdate(any(), any(), any(), any()))
                .thenReturn(List.of(first, second));
        when(assignmentRepository.markAssignmentsMissedByIds(any(), any(), any())).thenReturn(2);

        int result = service.markExpiredAssignmentsMissed(
                LocalDateTime.of(2026, 8, 7, 10, 0), 100);

        assertThat(result).isEqualTo(2);
        verify(activityCommandService).resetCurrentStreaksForMissedAssignments(List.of(10L, 11L));
    }

    @Test
    @DisplayName("마감 대상 그룹이 없으면 Assignment와 활동 상태를 조회하지 않는다")
    void markExpiredAssignmentsMissed_NoCandidate_ReturnsZero() {
        when(groupRepository.findExpiredAssignmentGroupsForUpdate(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        int result = service.markExpiredAssignmentsMissed(
                LocalDateTime.of(2026, 8, 7, 10, 0), 100);

        assertThat(result).isZero();
        verifyNoInteractions(assignmentRepository, activityCommandService);
    }
}
