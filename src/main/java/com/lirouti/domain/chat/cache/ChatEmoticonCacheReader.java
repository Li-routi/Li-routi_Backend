package com.lirouti.domain.chat.cache;

import com.lirouti.domain.chat.entity.ChatEmoticon;
import com.lirouti.domain.chat.repository.ChatEmoticonRepository;
import com.lirouti.global.config.RedisConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ChatEmoticonCacheReader {
    private static final String ACTIVE_EMOTICONS_KEY = "active";

    private final ChatEmoticonRepository chatEmoticonRepository;

    /**
     * 활성 이모티콘의 공통 필드만 Repository 노출 순서대로 조회한다.
     * 만료되는 자산 조회 URL은 포함하지 않는다.
     */
    @Cacheable(
            cacheNames = RedisConfig.CHAT_EMOTICONS_CACHE,
            key = "'" + ACTIVE_EMOTICONS_KEY + "'"
    )
    public List<CachedEmoticon> getActive() {
        // ArrayList의 root type 정보가 있어야 현재 generic serializer가 목록을 복원할 수 있다.
        return chatEmoticonRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(ChatEmoticonCacheReader::toCachedEmoticon)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static CachedEmoticon toCachedEmoticon(ChatEmoticon emoticon) {
        return new CachedEmoticon(
                emoticon.getId(),
                emoticon.getCode(),
                emoticon.getAssetKey(),
                emoticon.getContentType(),
                emoticon.getAnimated()
        );
    }

    /**
     * Redis에 저장하는 활성 채팅 이모티콘 공통 읽기 모델이다.
     * 필드 호환성이 깨지면 chatEmoticons cache version을 함께 올린다.
     */
    public record CachedEmoticon(
            Long id,
            String code,
            String assetKey,
            String contentType,
            Boolean animated
    ) {
    }
}
