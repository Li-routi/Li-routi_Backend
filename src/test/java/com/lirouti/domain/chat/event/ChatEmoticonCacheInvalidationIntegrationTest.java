package com.lirouti.domain.chat.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.RedisCache;

import io.micrometer.core.instrument.MeterRegistry;

import com.lirouti.domain.chat.dto.request.ChatReqDTO;
import com.lirouti.domain.chat.cache.ChatEmoticonCacheReader;
import com.lirouti.domain.chat.entity.ChatEmoticon;
import com.lirouti.domain.chat.repository.ChatEmoticonRepository;
import com.lirouti.domain.chat.service.command.ChatCommandService;
import com.lirouti.global.config.RedisConfig;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("채팅 이모티콘 캐시 무효화 통합 테스트")
class ChatEmoticonCacheInvalidationIntegrationTest {
    private static final String ACTIVE_KEY = "active";
    private static final String ASSET_KEY =
            "chat-emoticons/2026/08/14/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.png";

    @Autowired
    private ChatCommandService chatCommandService;
    @Autowired
    private ChatEmoticonRepository chatEmoticonRepository;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private MeterRegistry meterRegistry;

    private final List<Long> createdEmoticonIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> createdEmoticonIds.forEach(
                chatEmoticonRepository::deleteById));
        createdEmoticonIds.clear();
    }

    @AfterAll
    void cleanCache() {
        activeCache().evictIfPresent(ACTIVE_KEY);
    }

    @Test
    @Order(1)
    @DisplayName("등록 transaction이 commit되면 활성 이모티콘 캐시를 제거한다")
    void createEmoticonMetadata_Commit_EvictsActiveCache() {
        seedActiveCache();

        ChatEmoticon saved = commit(() -> chatCommandService.createEmoticonMetadata(
                registerRequest(uniqueCode()),
                ASSET_KEY,
                "image/png"
        ));
        createdEmoticonIds.add(saved.getId());

        assertThat(activeCache().get(ACTIVE_KEY)).isNull();
    }

    @Test
    @Order(0)
    @DisplayName("상태 변경 transaction이 rollback되면 기존 캐시를 유지한다")
    void updateEmoticonStatus_Rollback_PreservesActiveCache() {
        ChatEmoticon emoticon = commit(() -> chatEmoticonRepository.saveAndFlush(
                ChatEmoticon.builder()
                        .code(uniqueCode())
                        .assetKey(ASSET_KEY)
                        .contentType("image/png")
                        .animated(false)
                        .active(false)
                        .displayOrder(10)
                        .build()));
        createdEmoticonIds.add(emoticon.getId());
        seedActiveCache();

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            chatCommandService.updateEmoticonStatus(emoticon.getId(), true);
            status.setRollbackOnly();
        });

        assertThat(activeCache().get(ACTIVE_KEY)).isNotNull();
        assertThat(chatEmoticonRepository.findById(emoticon.getId()))
                .get()
                .extracting(ChatEmoticon::isActive)
                .isEqualTo(false);
    }

    @Test
    @Order(2)
    @DisplayName("캐시 제거 실패는 이미 커밋된 변경을 실패시키지 않는다")
    void listener_EvictFailure_DoesNotPropagate() {
        CacheManager cacheManager = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache(RedisConfig.CHAT_EMOTICONS_CACHE)).thenReturn(cache);
        doThrow(new RuntimeException("redis unavailable"))
                .when(cache)
                .evictIfPresent(ACTIVE_KEY);

        ChatEmoticonCacheInvalidationListener listener =
                new ChatEmoticonCacheInvalidationListener(cacheManager);

        assertThatCode(() -> listener.handle(new ChatEmoticonCacheInvalidatedEvent()))
                .doesNotThrowAnyException();
        verify(cache).evictIfPresent(ACTIVE_KEY);
    }

    @Test
    @Order(3)
    @DisplayName("활성 이모티콘 캐시의 실제 cache meter를 노출한다")
    void cacheManager_ExposesChatEmoticonMeter() {
        RedisCache cache = (RedisCache) activeCache();
        CacheStatistics before = cache.getStatistics();
        seedActiveCache();
        assertThat(activeCache().get(ACTIVE_KEY)).isNotNull();

        CacheStatistics statistics = cache.getStatistics();
        assertThat(statistics.getCacheName())
                .isEqualTo(RedisConfig.CHAT_EMOTICONS_CACHE);
        assertThat(statistics.getGets()).isGreaterThan(before.getGets());
        assertThat(statistics.getHits()).isGreaterThan(before.getHits());

        var hitMeter = meterRegistry.find("cache.gets")
                .tags("cache", RedisConfig.CHAT_EMOTICONS_CACHE, "result", "hit")
                .functionCounter();
        assertThat(hitMeter).isNotNull();
        assertThat(hitMeter.count()).isGreaterThan(0.0);
    }

    private Cache activeCache() {
        return cacheManager.getCache(RedisConfig.CHAT_EMOTICONS_CACHE);
    }

    private void seedActiveCache() {
        List<ChatEmoticonCacheReader.CachedEmoticon> cachedEmoticons = new ArrayList<>(List.of(
                new ChatEmoticonCacheReader.CachedEmoticon(
                        1L,
                        "sentinel",
                        "chat-emoticons/sentinel.png",
                        "image/png",
                        false
                )
        ));
        activeCache().evictIfPresent(ACTIVE_KEY);
        assertThat(activeCache().get(ACTIVE_KEY, () -> cachedEmoticons))
                .isEqualTo(cachedEmoticons);
    }

    private ChatEmoticon commit(ThrowingSupplier<ChatEmoticon> operation) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> operation.get());
    }

    private ChatReqDTO.RegisterEmoticon registerRequest(String code) {
        return new ChatReqDTO.RegisterEmoticon(code, "image/png", 10);
    }

    private String uniqueCode() {
        return "integration_" + System.nanoTime();
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get();
    }
}
