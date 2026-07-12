package com.lirouti.global.util;

import java.time.Duration;
import java.util.Collections;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisUtil {
    private static final RedisScript<Long> COMPARE_AND_SET_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
                return 1
            end
            return 0
            """,
            Long.class
    );

    private final RedisTemplate<String, Object> redisTemplate;

    // 데이터 저장
    public void set(String key, Object value, Duration duration) {
        redisTemplate.opsForValue().set(key, value, duration);
    }

    // 데이터 조회
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // 데이터를 가져오면서 즉시 삭제 (동시성 방어용)
    public Object getAndDelete(String key) {
        return redisTemplate.opsForValue().getAndDelete(key);
    }

    // 데이터 삭제
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    // 블랙리스트 토큰 확인
    public boolean isBlackList(String accessToken) {
        return hasKey(accessToken);
    }

    // 존재 여부 확인
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // 블랙리스트 등록 (key가 액세스 토큰, value가 "logout", ttl이 남은 유효 시간이 됨)
    public void setBlackList(String accessToken, Long remainingTime) {
        if (remainingTime > 0) {
            redisTemplate.opsForValue().set(accessToken, "logout", Expiration.milliseconds(remainingTime));
        }
    }

    public boolean compareAndSet(
            String key,
            String expectedValue,
            String newValue,
            Duration duration
    ) {
        Long result = redisTemplate.execute(
                COMPARE_AND_SET_SCRIPT,
                Collections.singletonList(key),
                expectedValue,
                newValue,
                String.valueOf(duration.toMillis())
        );
        return Long.valueOf(1L).equals(result);
    }
}
