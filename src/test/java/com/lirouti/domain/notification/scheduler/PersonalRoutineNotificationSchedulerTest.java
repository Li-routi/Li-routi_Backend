package com.lirouti.domain.notification.scheduler;

import com.lirouti.domain.notification.service.NotificationDispatchService;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.repository.MemberRoutineRepository;
import com.lirouti.domain.routine.service.query.RoutineCompletionSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("개인 루틴 알림 스케줄러 테스트")
class PersonalRoutineNotificationSchedulerTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private MemberRoutineRepository routineRepository;
    @Mock
    private RoutineCompletionSource completionSource;
    @Mock
    private NotificationDispatchService dispatchService;
    @Mock
    private MemberRoutine routine;

    @Test
    @DisplayName("23시 59분에도 역전된 시간 범위 없이 정확한 분으로 조회한다")
    void notifyPersonalRoutines_At2359_QueriesExactMinuteAcrossDateBoundary() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-11T14:59:00Z"), KST);
        PersonalRoutineNotificationScheduler scheduler =
                new PersonalRoutineNotificationScheduler(
                        routineRepository,
                        completionSource,
                        dispatchService,
                        clock
                );

        scheduler.notifyPersonalRoutines();

        verify(routineRepository).findDueForNotification(
                DayOfWeek.TUESDAY,
                LocalTime.of(23, 59),
                false
        );
        verify(routineRepository).findDueForNotification(
                DayOfWeek.WEDNESDAY,
                LocalTime.of(0, 59),
                true
        );
        verify(routineRepository).findDueForNotification(
                DayOfWeek.TUESDAY,
                LocalTime.of(23, 59),
                true
        );
    }

    @Test
    @DisplayName("인증 완료 여부를 한 번에 조회하고 완료 루틴 알림을 건너뛴다")
    void notifyPersonalRoutines_CompletedRoutine_SkipsDispatchWithBatchLookup() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-11T03:00:00Z"), KST);
        PersonalRoutineNotificationScheduler scheduler =
                new PersonalRoutineNotificationScheduler(
                        routineRepository,
                        completionSource,
                        dispatchService,
                        clock
                );
        when(routine.getId()).thenReturn(10L);
        when(routineRepository.findDueForNotification(
                DayOfWeek.TUESDAY,
                LocalTime.NOON,
                false
        )).thenReturn(List.of(routine));
        when(completionSource.findCompletedRoutineIds(
                List.of(10L),
                LocalDate.of(2026, 8, 11)
        )).thenReturn(Set.of(10L));

        scheduler.notifyPersonalRoutines();

        verify(completionSource).findCompletedRoutineIds(
                List.of(10L),
                LocalDate.of(2026, 8, 11)
        );
        verify(dispatchService, never()).dispatch(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
