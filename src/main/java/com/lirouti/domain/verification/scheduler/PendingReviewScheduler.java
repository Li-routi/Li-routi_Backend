package com.lirouti.domain.verification.scheduler;

import com.lirouti.domain.verification.service.PendingReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingReviewScheduler {

    private final PendingReviewService pendingReviewService;

    /**
     * 10분마다 보류된 인증을 다시 심사한다.
     *
     * <h3>{@code fixedDelay} 를 쓰는 것이 요점이다</h3>
     * <b>같은 실행이 겹치면 안 된다.</b> 겹치면 같은 건을 두 번 심사해 시도 횟수가 두 배로 오르고,
     * 상한에 절반 시간 만에 닿는다. {@code fixedDelay} 는 <b>앞 실행이 끝난 뒤부터</b> 간격을
     * 세므로 한 인스턴스 안에서는 겹치지 않는다({@code fixedRate} 는 시작 시각 기준이라 겹친다).
     *
     * <p>지금 운영은 단일 인스턴스라 이것으로 충분하다. <b>여러 대로 늘리면 인스턴스끼리는 겹친다</b> —
     * 그때는 분산 잠금이 필요하다.
     *
     * <p>배치가 예외로 죽으면 스케줄러 스레드에 스택만 찍히고 다음 실행은 정상적으로 돈다.
     * 다만 조용히 지나가지 않도록 여기서 잡아 로그를 남긴다.
     */
    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT2M")
    public void resweepPendingReviews() {
        try {
            pendingReviewService.sweepPending();
        } catch (RuntimeException e) {
            log.error("보류 인증 재심사 배치가 실패했습니다.", e);
        }
    }

    /**
     * 한 시간마다 보류 건수를 남긴다. <b>쌓이는 것을 아무도 모르는 상태가 가장 나쁘다.</b>
     *
     * <p>재심사 주기(10분)와 따로 두는 이유는, 재심사가 꺼져 있어도 건수는 보여야 하기 때문이다 —
     * 오히려 그때 더 필요하다.
     */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void reportPendingCount() {
        try {
            pendingReviewService.logPendingCount();
        } catch (RuntimeException e) {
            log.error("보류 건수 집계에 실패했습니다.", e);
        }
    }
}
