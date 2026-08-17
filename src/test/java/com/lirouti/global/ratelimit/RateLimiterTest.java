package com.lirouti.global.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimiter 테스트")
class RateLimiterTest {
    private static final String KEY = "rate-limit:media-presign:member:1";
    private static final Duration WINDOW = Duration.ofHours(1);
    private static final int LIMIT = 20;

    @Mock
    private StringRedisTemplate redisTemplate;

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter(redisTemplate);
    }

    /** 스크립트가 돌려주는 {카운트, 남은 밀리초}를 흉내낸다. */
    private void mockScript(long count, long ttlMillis) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(List.of(count, ttlMillis));
    }

    @Test
    @DisplayName("한도 안이면 통과한다")
    void consume_UnderLimit_Allowed() {
        mockScript(1, 3_600_000);

        RateLimiter.Result result = rateLimiter.consume(KEY, LIMIT, WINDOW);

        assertThat(result.allowed()).isTrue();
        assertThat(result.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("한도와 같은 횟수까지는 통과한다 — 경계에서 한 건을 손해 보지 않는다")
    void consume_ExactlyAtLimit_Allowed() {
        mockScript(LIMIT, 60_000);

        assertThat(rateLimiter.consume(KEY, LIMIT, WINDOW).allowed()).isTrue();
    }

    @Test
    @DisplayName("한도를 넘으면 막고 남은 시간을 초로 알려준다")
    void consume_OverLimit_BlockedWithRetryAfter() {
        mockScript(LIMIT + 1, 90_000);

        RateLimiter.Result result = rateLimiter.consume(KEY, LIMIT, WINDOW);

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterSeconds()).isEqualTo(90);
    }

    @Test
    @DisplayName("남은 시간이 1초 미만이어도 Retry-After는 0이 되지 않는다")
    void consume_SubSecondRemaining_RetryAfterAtLeastOne() {
        mockScript(LIMIT + 1, 200);

        assertThat(rateLimiter.consume(KEY, LIMIT, WINDOW).retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    @DisplayName("TTL이 없다고 나오면 창 길이로 되돌린다 — Retry-After가 음수가 되지 않게")
    void consume_NoTtl_FallsBackToWindow() {
        mockScript(LIMIT + 1, -1);

        assertThat(rateLimiter.consume(KEY, LIMIT, WINDOW).retryAfterSeconds())
                .isEqualTo(WINDOW.toSeconds());
    }

    @Test
    @DisplayName("스크립트 응답이 예상 형태가 아니면 예외를 던진다 — 조용히 통과시키지 않는다")
    void consume_MalformedScriptResult_Throws() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> rateLimiter.consume(KEY, LIMIT, WINDOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
