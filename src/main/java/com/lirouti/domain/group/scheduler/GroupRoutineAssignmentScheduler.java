package com.lirouti.domain.group.scheduler;

import com.lirouti.domain.group.service.command.GroupRoutineAssignmentCommandService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GroupRoutineAssignmentScheduler {
    private final GroupRoutineAssignmentCommandService assignmentCommandService;
    private final Clock clock;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void assignDailyGroupRoutines() {
        assignmentCommandService.assignScheduledRoutinesForDate(LocalDate.now(clock));
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void refreshAssignmentStatuses() {
        assignmentCommandService.refreshAssignmentStatuses(LocalDateTime.now(clock));
    }
}
