package com.lirouti.global.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * 심사 보류 건의 재심사 설정.
 *
 * <p>심사기가 답을 못 주면 인증을 보류하고, 여기 값에 따라 다시 심사한다. 유예가 끝나면
 * <b>결국 통과시킨다</b> — 사용자가 잘못한 것이 없는데 남의 장애로 반려하는 것은 부당하고,
 * 사진이 영영 안 보이는 것도 같은 이유로 부당하다.
 */
@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "ai.review.pending")
public class PendingReviewProperties {

    /**
     * 재심사를 켤지. 끄면 <b>보류 자체를 하지 않는다</b> — 심사가 답을 못 주면 지금처럼 바로
     * 통과시킨다.
     *
     * <p>탈출구다. 재심사가 오작동해 보류가 쌓이기만 하면, 코드를 되돌리는 것보다 이 값을 끄는
     * 편이 빠르다. {@code ai.review.enabled} 와 같은 성격이다.
     */
    private boolean enabled = true;

    /**
     * 보류 상한. 이 시간이 지나면 <b>승격하고 통과로 확정한다.</b>
     *
     * <p><b>대기 prefix 수명 주기(3일)보다 짧아야 한다.</b> 길면 재심사할 사진이 먼저 지워진다.
     * 상한과 수명 주기 사이의 이틀이 여유다 — 스케줄러가 상한 직후 바로 돌지 않을 수 있고,
     * 확정 처리도 한 번 더 실패할 수 있다.
     */
    @NotNull
    private Duration maxAge = Duration.ofHours(24);

    /**
     * 재심사 시도 상한. 이 횟수에 닿으면 상한과 같이 통과로 확정한다.
     *
     * <p>시간과 함께 두는 이유는 <b>스케줄러가 멈춰 있던 구간을 시간이 받아 주기</b> 때문이다.
     * 횟수만 두면 스케줄러가 죽어 있는 동안 보류가 영영 안 풀린다.
     */
    @Positive
    private int maxAttempts = 12;

    /** 한 번에 처리할 보류 건수. 장애가 길어 한꺼번에 쌓였을 때 한 실행이 과도해지지 않게 막는다. */
    @Positive
    private int batchSize = 100;
}
