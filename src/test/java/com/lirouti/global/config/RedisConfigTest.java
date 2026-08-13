package com.lirouti.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.context.support.TestPropertySourceUtils;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("RedisConfig 캐시 정책 테스트")
class RedisConfigTest {
    private final RedisConfig redisConfig = new RedisConfig();

    @Test
    @DisplayName("공통 캐시를 시작 시 등록하고 캐시별 TTL과 version prefix를 적용한다")
    void cacheManager_RegistersCacheSpecificPolicies() {
        RedisCacheManager cacheManager = createCacheManager(mock(RedisConnectionFactory.class));
        Map<String, RedisCacheConfiguration> configurations =
                cacheManager.getCacheConfigurations();

        assertThat(cacheManager.getCacheNames())
                .containsExactlyInAnyOrder(
                        RedisConfig.ROUTINE_TEMPLATES_CACHE,
                        RedisConfig.CHAT_EMOTICONS_CACHE);
        assertCachePolicy(
                configurations.get(RedisConfig.ROUTINE_TEMPLATES_CACHE),
                RedisConfig.ROUTINE_TEMPLATES_CACHE,
                Duration.ofHours(6));
        assertCachePolicy(
                configurations.get(RedisConfig.CHAT_EMOTICONS_CACHE),
                RedisConfig.CHAT_EMOTICONS_CACHE,
                Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("캐시 조회 통계를 기록하고 null 값 저장을 거부한다")
    void cacheManager_EnablesStatisticsAndRejectsNullValues() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisStringCommands stringCommands = mock(RedisStringCommands.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.stringCommands()).thenReturn(stringCommands);
        when(stringCommands.get(any(byte[].class))).thenReturn(null);

        RedisCacheManager cacheManager = createCacheManager(connectionFactory);
        RedisCache cache = (RedisCache) cacheManager.getCache(
                RedisConfig.ROUTINE_TEMPLATES_CACHE);

        assertThat(cache).isNotNull();
        assertThat(cache.get("all")).isNull();
        assertThat(cache.getStatistics().getGets()).isEqualTo(1);
        assertThat(cache.getStatistics().getMisses()).isEqualTo(1);
        assertThatThrownBy(() -> cache.put("all", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("캐시 interceptor가 오류를 기록하고 삼키는 handler를 사용한다")
    void errorHandler_IsWiredAndSwallowsCacheFailure() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context,
                    "spring.data.redis.host=localhost",
                    "spring.data.redis.port=6379",
                    "spring.data.redis.password=test-password");
            context.register(RedisConfig.class);
            context.refresh();

            CacheErrorHandler errorHandler = context.getBean(CacheErrorHandler.class);
            CacheInterceptor cacheInterceptor = context.getBean(CacheInterceptor.class);
            Cache cache = mock(Cache.class);
            when(cache.getName()).thenReturn(RedisConfig.ROUTINE_TEMPLATES_CACHE);

            assertThat(errorHandler).isInstanceOf(LoggingCacheErrorHandler.class);
            assertThat(cacheInterceptor.getErrorHandler()).isSameAs(errorHandler);
            assertThatCode(() -> errorHandler.handleCacheGetError(
                    new RuntimeException("redis unavailable"), cache, "all"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("일반 Redis와 인증 Redis의 기존 serializer 구성을 유지한다")
    void redisTemplates_KeepSeparateSerializers() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisSerializer<Object> serializer = objectSerializer();

        RedisTemplate<String, Object> redisTemplate =
                redisConfig.redisTemplate(connectionFactory, serializer);
        StringRedisTemplate authRedisTemplate =
                redisConfig.authRedisTemplate(connectionFactory);

        assertThat(redisTemplate.getValueSerializer()).isSameAs(serializer);
        assertThat(redisTemplate.getHashValueSerializer()).isSameAs(serializer);
        assertThat(authRedisTemplate.getKeySerializer())
                .isInstanceOf(StringRedisSerializer.class);
        assertThat(authRedisTemplate.getValueSerializer())
                .isInstanceOf(StringRedisSerializer.class);
    }

    private RedisCacheManager createCacheManager(RedisConnectionFactory connectionFactory) {
        RedisSerializer<Object> serializer = objectSerializer();
        RedisCacheManager cacheManager = (RedisCacheManager) redisConfig.cacheManager(
                connectionFactory,
                serializer);
        cacheManager.afterPropertiesSet();
        return cacheManager;
    }

    private RedisSerializer<Object> objectSerializer() {
        return redisConfig.redisSerializer();
    }

    private void assertCachePolicy(
            RedisCacheConfiguration configuration,
            String cacheName,
            Duration expectedTtl
    ) {
        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowCacheNullValues()).isFalse();
        assertThat(configuration.getKeyPrefixFor(cacheName))
                .isEqualTo("lirouti:cache:" + cacheName + ":v1::");
        assertThat(configuration.getTtlFunction().getTimeToLive("key", "value"))
                .isEqualTo(expectedTtl);
    }
}
