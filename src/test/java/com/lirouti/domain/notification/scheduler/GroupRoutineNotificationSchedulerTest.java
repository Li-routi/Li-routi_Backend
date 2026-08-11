package com.lirouti.domain.notification.scheduler;

import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.notification.service.NotificationDispatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("그룹 루틴 알림 스케줄러 테스트")
class GroupRoutineNotificationSchedulerTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<GroupRoutineAssignmentStatus> TERMINAL_STATUSES = List.of(
            GroupRoutineAssignmentStatus.COMPLETED,
            GroupRoutineAssignmentStatus.MISSED
    );

    @Mock
    private GroupRoutineAssignmentRepository assignmentRepository;
    @Mock
    private NotificationDispatchService dispatchService;

    @Test
    @DisplayName("23시 59분에는 그룹 저장소 계약을 유지하며 역전되지 않는 상한을 전달한다")
    void notifyGroupRoutineBoundaries_At2359_UsesNonWrappingUpperBound() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-11T14:59:00Z"), KST);
        GroupRoutineNotificationScheduler scheduler = new GroupRoutineNotificationScheduler(
                assignmentRepository,
                dispatchService,
                clock
        );

        scheduler.notifyGroupRoutineBoundaries();

        verify(assignmentRepository).findDueForNotification(
                LocalDate.of(2026, 8, 11),
                LocalTime.of(23, 59),
                LocalTime.MAX,
                true,
                TERMINAL_STATUSES
        );
        verify(assignmentRepository).findDueForNotification(
                LocalDate.of(2026, 8, 12),
                LocalTime.of(0, 59),
                LocalTime.of(1, 0),
                false,
                TERMINAL_STATUSES
        );
        verify(assignmentRepository).findEndingForNotification(
                LocalDate.of(2026, 8, 11),
                LocalTime.of(23, 59),
                LocalTime.MAX
        );
    }
}
