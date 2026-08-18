package com.lirouti.domain.notification.scheduler;

import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import com.lirouti.domain.notification.service.NotificationDispatchService;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.repository.MemberRoutineRepository;
import com.lirouti.domain.routine.service.query.RoutineCompletionSource;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** KST 분 경계마다 개인 루틴의 리마인드·마감 임박·미완료 알림을 처리한다. */
@Component
@RequiredArgsConstructor
public class PersonalRoutineNotificationScheduler {
    private static final String REFERENCE_TYPE = "MEMBER_ROUTINE";

    private final MemberRoutineRepository routineRepository;
    private final RoutineCompletionSource completionSource;
    private final NotificationDispatchService dispatchService;
    private final Clock clock;

    /** 현재 분의 리마인드, 한 시간 뒤 마감, 현재 분 미완료 알림을 순서대로 처리한다. */
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void notifyPersonalRoutines() {
        LocalDateTime now = currentMinute();
        notifyBoundary(now, false);
        notifyBoundary(now.plusHours(1), true);
        notifyMissed(now);
    }

    private void notifyBoundary(LocalDateTime boundary, boolean deadline) {
        List<MemberRoutine> routines = routineRepository.findDueForNotification(
                boundary.getDayOfWeek(),
                boundary.toLocalTime(),
                deadline
        );
        Set<Long> completedIds = completedIds(routines, boundary);
        NotificationType type = deadline
                ? NotificationType.PERSONAL_ROUTINE_DEADLINE
                : NotificationType.PERSONAL_ROUTINE_REMINDER;

        for (MemberRoutine routine : routines) {
            if (completedIds.contains(routine.getId())) {
                continue;
            }
            dispatchService.dispatch(
                    routine.getMember().getId(),
                    NotificationCategory.PERSONAL_ROUTINE,
                    type,
                    deadline ? "루틴 마감까지 1시간 남았어요!" : routine.getName() + ", 지금 시작해 볼까요?",
                    deadline ? routine.getName() + "을 완료하고 오늘 기록을 채워보세요 🔥"
                            : "작은 행동 하나가 오늘의 흐름을 만들어요.",
                    null,
                    routine.getId(),
                    REFERENCE_TYPE,
                    "personal:" + type + ":" + routine.getId() + ":" + boundary.toLocalDate()
            );
        }
    }

    private void notifyMissed(LocalDateTime boundary) {
        List<MemberRoutine> routines = routineRepository.findDueForNotification(
                boundary.getDayOfWeek(),
                boundary.toLocalTime(),
                true
        );
        Set<Long> completedIds = completedIds(routines, boundary);
        for (MemberRoutine routine : routines) {
            if (completedIds.contains(routine.getId())) {
                continue;
            }
            dispatchService.dispatch(
                    routine.getMember().getId(),
                    NotificationCategory.PERSONAL_ROUTINE,
                    NotificationType.PERSONAL_ROUTINE_MISSED,
                    "오늘의 약속, 아직 기다리고 있어요",
                    routine.getName() + "을 놓쳤어요. 내일은 작은 한 번부터 다시 시작해요!",
                    null,
                    routine.getId(),
                    REFERENCE_TYPE,
                    "personal:missed:" + routine.getId() + ":" + boundary.toLocalDate()
            );
        }
    }

    private Set<Long> completedIds(List<MemberRoutine> routines, LocalDateTime boundary) {
        if (routines.isEmpty()) {
            return Set.of();
        }
        return completionSource.findCompletedRoutineIds(
                routines.stream().map(MemberRoutine::getId).toList(),
                boundary.toLocalDate()
        );
    }

    private LocalDateTime currentMinute() {
        return LocalDateTime.now(clock).withSecond(0).withNano(0);
    }
}
