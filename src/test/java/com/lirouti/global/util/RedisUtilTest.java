package com.lirouti.global.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisUtil 테스트")
class RedisUtilTest {
    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("compareAndSet이 script 결과 1이면 true를 반환한다")
    void compareAndSet_ReturnsTrueWhenScriptReturnsOne() {
        // given
        RedisUtil redisUtil = new RedisUtil(redisTemplate);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(Collections.singletonList("auth:refresh:1")),
                eq("expected-hash"),
                eq("new-hash"),
                eq("1000")
        )).thenReturn(1L);

        // when
        boolean result = redisUtil.compareAndSet(
                "auth:refresh:1",
                "expected-hash",
                "new-hash",
                Duration.ofSeconds(1)
        );

        // then
        assertThat(result).isTrue();
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(Collections.singletonList("auth:refresh:1")),
                eq("expected-hash"),
                eq("new-hash"),
                eq("1000")
        );
    }

    @Test
    @DisplayName("compareAndSet이 script 결과 0이면 false를 반환한다")
    void compareAndSet_ReturnsFalseWhenScriptReturnsZero() {
        // given
        RedisUtil redisUtil = new RedisUtil(redisTemplate);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(Collections.singletonList("auth:refresh:1")),
                eq("expected-hash"),
                eq("new-hash"),
                eq("1000")
        )).thenReturn(0L);

        // when
        boolean result = redisUtil.compareAndSet(
                "auth:refresh:1",
                "expected-hash",
                "new-hash",
                Duration.ofSeconds(1)
        );

        // then
        assertThat(result).isFalse();
    }
}
