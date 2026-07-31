package com.lirouti.global.ratelimit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Redis 카운터 기반 요청 빈도 제한(#23).
 *
 * <b>고정 창(fixed window)</b> 방식이다. 창 경계에서 최대 2배까지 몰릴 수 있지만(창 끝에 한도만큼,
 * 다음 창 시작에 또 한도만큼), 목적이 정밀한 제어가 아니라 <b>스토리지 남용 억제</b>라 이 정도
 * 오차는 감수한다. 슬라이딩 윈도우는 요청마다 정렬 셋을 쌓아야 해서 비용이 크다.
 */
@Component
public class RateLimiter {
    /**
     * INCR과 만료 설정을 한 번에 한다.
     *
     * 두 명령을 나눠 보내면 <b>INCR 직후 앱이 죽었을 때 만료 없는 키가 남아</b> 그 사용자가
     * 영구히 차단된다. Lua는 원자적으로 실행되므로 그 틈이 없다.
     *
     * 반환은 {현재 카운트, 남은 밀리초}다. PTTL을 함께 돌려줘 Retry-After를 만든다.
     */
    private static final RedisScript<List> INCREMENT_WITH_EXPIRY = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return { current, redis.call('PTTL', KEYS[1]) }
            """, List.class);

    private final StringRedisTemplate redisTemplate;

    public RateLimiter(@Qualifier("authRedisTemplate") StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 요청 하나를 소비하고 결과를 돌려준다. Redis 호출이 실패하면 예외가 그대로 올라간다 —
     * 호출부(인터셉터)가 잡아서 통과시킨다(fail-open).
     */
    public Result consume(String key, int limit, Duration window) {
        List<?> result = redisTemplate.execute(
                INCREMENT_WITH_EXPIRY, List.of(key), String.valueOf(window.toMillis()));

        long count = toLong(result, 0);
        long ttlMillis = toLong(result, 1);

        // PTTL은 키에 만료가 없으면 -1, 키가 없으면 -2를 준다. 스크립트가 만료를 항상 걸지만
        // 방어적으로 창 길이로 되돌려 Retry-After가 음수가 되지 않게 한다.
        long remainingMillis = ttlMillis > 0 ? ttlMillis : window.toMillis();

        return new Result(count <= limit, count, retryAfterSeconds(remainingMillis));
    }

    /** Retry-After는 초 단위 정수다. 남은 시간이 1초 미만이어도 0을 주지 않도록 올림한다. */
    private static long retryAfterSeconds(long remainingMillis) {
        return Math.max(1, (remainingMillis + 999) / 1000);
    }

    private static long toLong(List<?> result, int index) {
        if (result == null || result.size() <= index || !(result.get(index) instanceof Number n)) {
            throw new IllegalStateException("레이트 리밋 스크립트 응답이 올바르지 않습니다: " + result);
        }
        return n.longValue();
    }

    /**
     * @param allowed          한도 안이면 true
     * @param count            이번 요청을 포함한 현재 창의 누적 요청 수
     * @param retryAfterSeconds 창이 초기화되기까지 남은 초
     */
    public record Result(boolean allowed, long count, long retryAfterSeconds) {
    }
}
