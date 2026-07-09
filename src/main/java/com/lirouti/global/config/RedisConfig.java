package com.lirouti.global.config;

import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import io.lettuce.core.api.StatefulConnection;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {
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

	@Bean
	public CacheManager cacheManager(
			RedisConnectionFactory connectionFactory,
			RedisSerializer<Object> serializer
	) {
        // 키와 값의 직렬화 방식 및 TTL 설정
		RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
				.serializeKeysWith(RedisSerializationContext.SerializationPair
						.fromSerializer(new StringRedisSerializer()))
				.serializeValuesWith(
						RedisSerializationContext.SerializationPair.fromSerializer(serializer))
				.entryTtl(Duration.ofHours(1));

		return RedisCacheManager.RedisCacheManagerBuilder
				.fromConnectionFactory(connectionFactory)
				.cacheDefaults(config)
				.build();
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
