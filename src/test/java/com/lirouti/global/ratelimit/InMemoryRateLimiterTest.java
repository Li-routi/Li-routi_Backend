package com.lirouti.global.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Redis 장애 시 쓰는 인메모리 레이트 리밋")
class InMemoryRateLimiterTest {

    /** 창 경계를 눈으로 확인해야 해서 시간을 직접 움직인다. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-08-05T00:00:00Z");

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    @Test
    @DisplayName("한도를 넘으면 막는다 — 이것이 없으면 장애 중 무제한이었다")
    void consume_OverLimit_Blocks() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(new MovableClock());

        for (int i = 1; i <= 3; i++) {
            assertThat(limiter.consume("k", 3, Duration.ofHours(1)).allowed())
                    .as("%d번째는 한도 안이다", i)
                    .isTrue();
        }

        RateLimiter.Result blocked = limiter.consume("k", 3, Duration.ofHours(1));
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.count()).isEqualTo(4);
        assertThat(blocked.retryAfterSeconds()).isPositive();
    }

    @Test
    @DisplayName("창이 지나면 다시 센다")
    void consume_AfterWindow_Resets() {
        MovableClock clock = new MovableClock();
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

        limiter.consume("k", 1, Duration.ofMinutes(10));
        assertThat(limiter.consume("k", 1, Duration.ofMinutes(10)).allowed()).isFalse();

        clock.advance(Duration.ofMinutes(10));

        assertThat(limiter.consume("k", 1, Duration.ofMinutes(10)).allowed())
                .as("창이 지났으므로 새로 센다")
                .isTrue();
    }

    @Test
    @DisplayName("키가 다르면 서로 영향이 없다")
    void consume_DifferentKeys_AreIndependent() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(new MovableClock());

        limiter.consume("member:1", 1, Duration.ofHours(1));

        assertThat(limiter.consume("member:2", 1, Duration.ofHours(1)).allowed()).isTrue();
    }

    @Test
    @DisplayName("만료된 항목은 걷어낸다 — 장애가 길어져도 메모리가 계속 늘지 않는다")
    void consume_ExpiredEntries_AreEvicted() {
        MovableClock clock = new MovableClock();
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

        for (int i = 0; i < 10_000; i++) {
            limiter.consume("ip:10.0." + (i / 256) + "." + (i % 256), 100, Duration.ofMinutes(1));
        }
        assertThat(limiter.size()).isEqualTo(10_000);

        clock.advance(Duration.ofMinutes(2));
        // 상한에 닿은 상태에서 새 키가 오면 만료분을 먼저 걷어낸다.
        limiter.consume("ip:new", 100, Duration.ofMinutes(1));

        assertThat(limiter.size())
                .as("만료된 10,000개가 빠지고 새 키 하나만 남는다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 키가 동시에 들어와도 저장소가 상한을 넘지 않는다")
    void consume_ConcurrentNewKeys_RespectsMaxEntries() throws Exception {
        MovableClock clock = new MovableClock();
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

        // 상한 직전까지 만료되지 않는 항목으로 채운다.
        for (int i = 0; i < 9_999; i++) {
            limiter.consume("fill:" + i, 100, Duration.ofHours(1));
        }
        assertThat(limiter.size()).isEqualTo(9_999);

        // 남은 자리는 하나인데 서로 다른 키 64개가 동시에 들어온다.
        int threads = 64;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < threads; i++) {
            int id = i;
            pool.submit(() -> {
                start.await();
                limiter.consume("burst:" + id, 100, Duration.ofHours(1));
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(limiter.size())
                .as("용량 검사와 삽입이 갈라져 있으면 여기서 상한을 넘긴다")
                .isLessThanOrEqualTo(10_000);
    }

    @Test
    @DisplayName("같은 키에 동시에 들어와도 한도를 넘겨 통과시키지 않는다")
    void consume_Concurrent_DoesNotOverAllow() throws Exception {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(new MovableClock());
        int threads = 32;
        int limit = 10;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                start.await();
                if (limiter.consume("hot", limit, Duration.ofHours(1)).allowed()) {
                    allowed.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(allowed.get())
                .as("허용된 요청이 한도를 넘으면 compute 바깥에서 카운트가 갈라진 것이다")
                .isEqualTo(limit);
    }
}
