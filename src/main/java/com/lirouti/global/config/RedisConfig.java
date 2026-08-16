package com.lirouti.global.config;

import java.time.Duration;
import java.util.Map;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import io.lettuce.core.api.StatefulConnection;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {
	public static final String ROUTINE_TEMPLATES_CACHE = "routineTemplates";
	public static final String CHAT_EMOTICONS_CACHE = "chatEmoticons";

	private static final Duration DEFAULT_CACHE_TTL = Duration.ofHours(1);
	private static final Duration ROUTINE_TEMPLATES_TTL = Duration.ofHours(6);
	private static final Duration CHAT_EMOTICONS_TTL = Duration.ofMinutes(30);
	private static final String CACHE_KEY_PREFIX = "lirouti:cache:";
	private static final String CACHE_VERSION = ":v1::";

	@Value("${spring.data.redis.host}")
	private String host;

	@Value("${spring.data.redis.port}")
	private int port;

	@Value("${spring.data.redis.password}")
	private String password;

	@Bean
	public RedisConnectionFactory redisConnectionFactory() {
		// 커넥션 풀 설정
		GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig = new GenericObjectPoolConfig<>();
		poolConfig.setMaxTotal(8); // 최대 활성 커넥션 수
		poolConfig.setMaxIdle(8); // 최대 유휴 커넥션 수
		poolConfig.setMinIdle(0); // 최소 유휴 커넥션 수

		// Lettuce 풀링 클라이언트 설정
		LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
				.poolConfig(poolConfig)
				.build();

		// 서버 설정
		RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration(host, port);
		serverConfig.setPassword(password);

		return new LettuceConnectionFactory(serverConfig, clientConfig);
	}

	@Bean
	public RedisTemplate<String, Object> redisTemplate(
			RedisConnectionFactory connectionFactory,
			RedisSerializer<Object> serializer
	) {
		RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
		redisTemplate.setConnectionFactory(connectionFactory);

		redisTemplate.setKeySerializer(new StringRedisSerializer());
		redisTemplate.setValueSerializer(serializer);
		redisTemplate.setHashKeySerializer(new StringRedisSerializer());
		redisTemplate.setHashValueSerializer(serializer);

		return redisTemplate;
	}

	// auth용 RedisTemplate를 별도로 생성하여 auth 관련 데이터만 관리하도록 설정
	@Bean
	public StringRedisTemplate authRedisTemplate(RedisConnectionFactory connectionFactory) {
		// StringRedisTemplate를 사용하여 Redis에 문자열 데이터를 저장하고 조회할 수 있도록 설정
		StringRedisTemplate redisTemplate = new StringRedisTemplate();
		redisTemplate.setConnectionFactory(connectionFactory);
		redisTemplate.setKeySerializer(new StringRedisSerializer());
		redisTemplate.setValueSerializer(new StringRedisSerializer());
		redisTemplate.setHashKeySerializer(new StringRedisSerializer());
		redisTemplate.setHashValueSerializer(new StringRedisSerializer());

		return redisTemplate;
	}

	@Bean
	public CacheManager cacheManager(
			RedisConnectionFactory connectionFactory,
			RedisSerializer<Object> serializer
	) {
		RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
				.serializeKeysWith(RedisSerializationContext.SerializationPair
						.fromSerializer(new StringRedisSerializer()))
				.serializeValuesWith(
						RedisSerializationContext.SerializationPair.fromSerializer(serializer))
				.computePrefixWith(cacheName -> CACHE_KEY_PREFIX + cacheName + CACHE_VERSION)
				.disableCachingNullValues()
				.entryTtl(DEFAULT_CACHE_TTL);

		Map<String, RedisCacheConfiguration> cacheConfigurations = Map.of(
				ROUTINE_TEMPLATES_CACHE, defaultConfig.entryTtl(ROUTINE_TEMPLATES_TTL),
				CHAT_EMOTICONS_CACHE, defaultConfig.entryTtl(CHAT_EMOTICONS_TTL)
		);

		return RedisCacheManager.RedisCacheManagerBuilder
				.fromConnectionFactory(connectionFactory)
				.cacheDefaults(defaultConfig)
				.withInitialCacheConfigurations(cacheConfigurations)
				.enableStatistics()
				.build();
	}

	@Bean
	@Override
	public CacheErrorHandler errorHandler() {
		return new LoggingCacheErrorHandler(RedisConfig.class.getName(), true);
	}

	@Bean
	public RedisSerializer<Object> redisSerializer() {
		// 허용된 타입만 역직렬화 가능하도록 제한
		PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
				.allowIfSubType("com.lirouti") // 허용된 패키지
				.allowIfSubType("java.util")
				.build();

        // GenericJacksonJsonRedisSerializer를 사용하여 JSON 직렬화 및 역직렬화
		return GenericJacksonJsonRedisSerializer.builder()
				.enableDefaultTyping(ptv)
				.build();
	}
}
