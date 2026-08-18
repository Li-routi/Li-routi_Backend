package com.lirouti.domain.chat.cache;

import com.lirouti.domain.chat.cache.ChatEmoticonCacheReader.CachedEmoticon;
import com.lirouti.domain.chat.entity.ChatEmoticon;
import com.lirouti.domain.chat.repository.ChatEmoticonRepository;
import com.lirouti.global.config.RedisConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ChatEmoticonCacheReader 테스트")
class ChatEmoticonCacheReaderTest {

    @Test
    @DisplayName("활성 이모티콘의 공통 필드만 Repository 순서대로 캐싱한다")
    void getActive_CachesCommonFieldsInRepositoryOrder() {
        try (AnnotationConfigApplicationContext context = createContext()) {
            ChatEmoticonRepository repository = context.getBean(ChatEmoticonRepository.class);
            ChatEmoticonCacheReader cacheReader = context.getBean(ChatEmoticonCacheReader.class);
            CacheManager cacheManager = context.getBean(CacheManager.class);
            when(repository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(
                    emoticon(11L, "hello", "chat-emoticons/hello.png", "image/png", false),
                    emoticon(21L, "dance", "chat-emoticons/dance.webp", "image/webp", true)
            ));

            List<CachedEmoticon> first = cacheReader.getActive();
            List<CachedEmoticon> second = cacheReader.getActive();

            assertThat(first).containsExactly(
                    new CachedEmoticon(
                            11L,
                            "hello",
                            "chat-emoticons/hello.png",
                            "image/png",
                            false
                    ),
                    new CachedEmoticon(
                            21L,
                            "dance",
                            "chat-emoticons/dance.webp",
                            "image/webp",
                            true
                    )
            );
            assertThat(second).isEqualTo(first);
            assertThat(cacheManager.getCache(RedisConfig.CHAT_EMOTICONS_CACHE))
                    .isNotNull()
                    .extracting(cache -> cache.get("active", List.class))
                    .isEqualTo(first);
            verify(repository).findAllByActiveTrueOrderByDisplayOrderAscIdAsc();
        }
    }

    @Test
    @DisplayName("빈 활성 이모티콘 목록도 정상 결과로 캐싱한다")
    void getActive_CachesEmptyList() {
        try (AnnotationConfigApplicationContext context = createContext()) {
            ChatEmoticonRepository repository = context.getBean(ChatEmoticonRepository.class);
            ChatEmoticonCacheReader cacheReader = context.getBean(ChatEmoticonCacheReader.class);
            when(repository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc()).thenReturn(List.of());

            assertThat(cacheReader.getActive()).isEmpty();
            assertThat(cacheReader.getActive()).isEmpty();

            verify(repository).findAllByActiveTrueOrderByDisplayOrderAscIdAsc();
        }
    }

    @Test
    @DisplayName("캐시 모델은 URL과 관리자 상태를 제외한 공통 필드 다섯 개만 가진다")
    void cachedEmoticon_ContainsOnlyCommonFields() {
        assertThat(CachedEmoticon.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("id", "code", "assetKey", "contentType", "animated");
    }

    @Test
    @DisplayName("현재 Redis serializer가 캐시 모델 목록과 빈 목록을 왕복 보존한다")
    void cachedEmoticon_RoundTripsWithRedisSerializer() {
        RedisSerializer<Object> serializer = new RedisConfig().redisSerializer();
        List<CachedEmoticon> emoticons = new ArrayList<>(List.of(
                new CachedEmoticon(
                        11L,
                        "hello",
                        "chat-emoticons/hello.png",
                        "image/png",
                        false
                ),
                new CachedEmoticon(
                        21L,
                        "dance",
                        "chat-emoticons/dance.webp",
                        "image/webp",
                        true
                )
        ));

        assertThat(roundTrip(serializer, emoticons)).isEqualTo(emoticons);
        assertThat(roundTrip(serializer, new ArrayList<>())).isEqualTo(List.of());
    }

    private AnnotationConfigApplicationContext createContext() {
        return new AnnotationConfigApplicationContext(CacheTestConfig.class);
    }

    private Object roundTrip(RedisSerializer<Object> serializer, Object value) {
        byte[] serialized = serializer.serialize(value);
        return serializer.deserialize(serialized);
    }

    private ChatEmoticon emoticon(
            Long id,
            String code,
            String assetKey,
            String contentType,
            boolean animated
    ) {
        ChatEmoticon emoticon = ChatEmoticon.builder()
                .code(code)
                .assetKey(assetKey)
                .contentType(contentType)
                .animated(animated)
                .active(true)
                .displayOrder(0)
                .build();
        ReflectionTestUtils.setField(emoticon, "id", id);
        return emoticon;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class CacheTestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(RedisConfig.CHAT_EMOTICONS_CACHE);
        }

        @Bean
        ChatEmoticonRepository chatEmoticonRepository() {
            return mock(ChatEmoticonRepository.class);
        }

        @Bean
        ChatEmoticonCacheReader chatEmoticonCacheReader(
                ChatEmoticonRepository chatEmoticonRepository
        ) {
            return new ChatEmoticonCacheReader(chatEmoticonRepository);
        }
    }
}
