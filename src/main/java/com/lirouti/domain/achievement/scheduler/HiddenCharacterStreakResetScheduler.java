package com.lirouti.domain.achievement.scheduler;

import com.lirouti.domain.achievement.service.command.HiddenCharacterStreakResetBatchService;
import com.lirouti.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** 매일 KST 00:05에 어제 하루의 노아·파도 연속 기록 미완료를 확정해 초기화한다. */
@Component
@RequiredArgsConstructor
public class HiddenCharacterStreakResetScheduler {

    private final HiddenCharacterStreakResetBatchService batchService;

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void resetMissedStreaks() {
        LocalDate yesterday = LocalDate.now(TimeUtil.KST).minusDays(1);
        batchService.resetMorningRoutineStreaksForMissedDay(yesterday);
        batchService.resetWaveRoutineStreaksForMissedDay(yesterday);
    }
}
