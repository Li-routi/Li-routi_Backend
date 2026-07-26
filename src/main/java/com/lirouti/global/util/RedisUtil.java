package com.lirouti.global.util;

import java.time.Duration;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisUtil {
    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

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

    private final StringRedisTemplate redisTemplate;

    // authRedisTemplate을 주입받아 초기화
    public RedisUtil(@Qualifier("authRedisTemplate") StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 데이터 저장
    public void set(String key, String value, Duration duration) {
        redisTemplate.opsForValue().set(key, value, duration);
    }

    // 데이터 조회
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // 데이터를 가져오면서 즉시 삭제 (동시성 방어용)
    public String getAndDelete(String key) {
        return redisTemplate.opsForValue().getAndDelete(key);
    }

    // 데이터 삭제
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    // 블랙리스트 토큰 확인
    public boolean isBlackList(String accessToken) {
        return hasKey(getBlacklistKey(accessToken));
    }

    // 존재 여부 확인
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // 블랙리스트 등록 (key가 액세스 토큰 해시, value가 "logout", ttl이 남은 유효 시간이 됨)
    public void setBlackList(String accessToken, Long remainingTime) {
        if (remainingTime > 0) {
            redisTemplate.opsForValue().set(
                    getBlacklistKey(accessToken),
                    "logout",
                    Duration.ofMillis(remainingTime)
            );
        }
    }

    private String getBlacklistKey(String accessToken) {
        return BLACKLIST_KEY_PREFIX + TokenHashUtil.hash(accessToken);
    }

    public boolean compareAndSet(
            String key,
            String expectedValue,
            String newValue,
            Duration duration
    ) {
        // Redis Lua 스크립트를 사용하여 원자적으로 비교 후 설정
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
