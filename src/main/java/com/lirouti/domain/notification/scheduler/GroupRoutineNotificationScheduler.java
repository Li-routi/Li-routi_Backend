package com.lirouti.domain.notification.scheduler;

import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import com.lirouti.domain.notification.service.NotificationDispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** KST 분 경계마다 그룹 루틴의 시작·마감 임박·종료 알림을 처리한다. */
@Component
@RequiredArgsConstructor
public class GroupRoutineNotificationScheduler {
    private static final String REFERENCE_TYPE = "GROUP_ROUTINE_ASSIGNMENT";
    private static final List<GroupRoutineAssignmentStatus> TERMINAL_STATUSES = List.of(
            GroupRoutineAssignmentStatus.COMPLETED,
            GroupRoutineAssignmentStatus.MISSED
    );

    private final GroupRoutineAssignmentRepository assignmentRepository;
    private final NotificationDispatchService dispatchService;
    private final Clock clock;

    /** 현재 분의 시작·종료와 한 시간 뒤 마감 임박 알림을 처리한다. */
    @Scheduled(cron = "15 * * * * *", zone = "Asia/Seoul")
    public void notifyGroupRoutineBoundaries() {
        LocalDateTime now = currentMinute();
        notifyBoundary(now, true, NotificationType.GROUP_ROUTINE_STARTED);
        notifyBoundary(now.plusHours(1), false, NotificationType.GROUP_ROUTINE_DEADLINE);
        notifyEnded(now);
    }

    private void notifyBoundary(
            LocalDateTime boundary,
            boolean start,
            NotificationType type
    ) {
        List<GroupRoutineAssignment> assignments = assignmentRepository.findDueForNotification(
                boundary.toLocalDate(),
                boundary.toLocalTime(),
                exclusiveMinuteEnd(boundary.toLocalTime()),
                start,
                TERMINAL_STATUSES
        );
        for (GroupRoutineAssignment assignment : assignments) {
            String routineName = assignment.getGroupRoutine().getTitle();
            dispatchService.dispatch(
                    assignment.getMember().getId(),
                    NotificationCategory.GROUP_ROUTINE,
                    type,
                    start ? "그룹 루틴이 시작됐어요" : "그룹 루틴 마감까지 1시간!",
                    start ? routineName + "을 함께 시작해 보세요."
                            : routineName + " 인증을 잊지 마세요 🔥",
                    assignment.getGroupRoutine().getGroup().getId(),
                    assignment.getId(),
                    REFERENCE_TYPE,
                    "group-boundary:" + type + ":" + assignment.getId()
            );
        }
    }

    private void notifyEnded(LocalDateTime boundary) {
        for (GroupRoutineAssignment assignment : assignmentRepository.findEndingForNotification(
                boundary.toLocalDate(),
                boundary.toLocalTime(),
                exclusiveMinuteEnd(boundary.toLocalTime())
        )) {
            dispatchService.dispatch(
                    assignment.getMember().getId(),
                    NotificationCategory.GROUP_ROUTINE,
                    NotificationType.GROUP_ROUTINE_ENDED,
                    "그룹 루틴이 종료됐어요",
                    assignment.getGroupRoutine().getTitle() + "의 오늘 일정이 끝났어요.",
                    assignment.getGroupRoutine().getGroup().getId(),
                    assignment.getId(),
                    REFERENCE_TYPE,
                    "group-ended:" + assignment.getId()
            );
        }
    }

    private LocalDateTime currentMinute() {
        return LocalDateTime.now(clock).withSecond(0).withNano(0);
    }

    /**
     * 저장소의 {@code [from, to)} 조건을 유지하면서 자정 직전에도 상한이 시작보다 작아지지
     * 않게 한다. 23:59의 다음 분인 00:00은 다음 스케줄 실행이 담당한다.
     */
    private static LocalTime exclusiveMinuteEnd(LocalTime start) {
        return start.getHour() == 23 && start.getMinute() == 59
                ? LocalTime.MAX
                : start.plusMinutes(1);
    }
}
