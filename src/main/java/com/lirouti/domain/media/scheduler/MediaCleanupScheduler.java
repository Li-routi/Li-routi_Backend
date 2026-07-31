package com.lirouti.domain.media.scheduler;

import com.lirouti.domain.media.service.MediaCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaCleanupScheduler {

    private final MediaCleanupService mediaCleanupService;
    private final Clock clock;

    /**
     * 한국 시간 기준 매일 새벽 4시에 미참조 미디어를 정리한다.
     *
     * <p>자정을 피한 이유는 그때 그룹 루틴 할당 배치가 돌기 때문이다. 둘 다 무겁진 않지만
     * 같은 순간에 겹칠 이유도 없고, 사용자 활동이 가장 적은 시간대이기도 하다.
     *
     * <p>정리 대상은 유예 기간이 지난 날짜라서 <b>실행 시각이 늦어도 손해가 없다.</b>
     * 하루를 통째로 거르더라도 다음 실행이 소급해서 훑는다(catch-up-days).
     *
     * <p>배치가 예외로 죽으면 스케줄러 스레드에 스택만 찍히고 다음 실행은 정상적으로 돈다.
     * 다만 조용히 지나가지 않도록 여기서 잡아 로그를 남긴다 — 삭제 작업이라 실패를
     * 모르고 지나가는 쪽이 더 나쁘다.
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void sweepOrphanMedia() {
        try {
            mediaCleanupService.sweepOrphans(LocalDate.now(clock));
        } catch (RuntimeException e) {
            log.error("미참조 미디어 정리 배치가 실패했습니다.", e);
        }
    }
}
