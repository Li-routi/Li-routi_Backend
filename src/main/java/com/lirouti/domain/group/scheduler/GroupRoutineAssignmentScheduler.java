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

    /** 한국 시간 기준 매일 자정에 해당 요일의 그룹 루틴 할당을 생성한다. */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void assignDailyGroupRoutines() {
        assignmentCommandService.assignScheduledRoutinesForDate(LocalDate.now(clock));
    }

    /** 한국 시간 기준 매분 시작·마감 경계에 맞춰 미완료 할당 상태를 갱신한다. */
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void refreshAssignmentStatuses() {
        assignmentCommandService.refreshAssignmentStatuses(LocalDateTime.now(clock));
    }
}
