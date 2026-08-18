package com.lirouti.domain.chat.event;

import com.lirouti.global.config.RedisConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEmoticonCacheInvalidationListener {
    private static final String ACTIVE_EMOTICONS_KEY = "active";

    private final CacheManager cacheManager;

    /** 이모티콘 변경 transaction이 커밋된 뒤 활성 목록 캐시만 제거한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChatEmoticonCacheInvalidatedEvent event) {
        Cache cache = cacheManager.getCache(RedisConfig.CHAT_EMOTICONS_CACHE);
        if (cache == null) {
            log.warn(
                    "채팅 이모티콘 캐시를 찾지 못해 무효화를 건너뛰었습니다. cacheName={}, key={}",
                    RedisConfig.CHAT_EMOTICONS_CACHE,
                    ACTIVE_EMOTICONS_KEY
            );
            return;
        }

        try {
            cache.evictIfPresent(ACTIVE_EMOTICONS_KEY);
        } catch (RuntimeException exception) {
            log.error(
                    "채팅 이모티콘 캐시 무효화에 실패했습니다. cacheName={}, key={}, exceptionType={}",
                    RedisConfig.CHAT_EMOTICONS_CACHE,
                    ACTIVE_EMOTICONS_KEY,
                    exception.getClass().getSimpleName(),
                    exception
            );
        }
    }
}
