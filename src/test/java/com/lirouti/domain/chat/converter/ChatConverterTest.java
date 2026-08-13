package com.lirouti.domain.chat.converter;

import com.lirouti.domain.chat.cache.ChatEmoticonCacheReader.CachedEmoticon;
import com.lirouti.domain.chat.dto.response.ChatResDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("ChatConverter 테스트")
class ChatConverterTest {

    @Test
    @DisplayName("캐시된 공통 필드에 요청 시점 URL을 붙이고 노출 순서를 유지한다")
    void toEmoticonListFromCache_CombinesAssetUrlsInCacheOrder() {
        List<CachedEmoticon> emoticons = List.of(
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
        Map<Long, String> assetUrls = Map.of(
                21L, "https://example.com/dance",
                11L, "https://example.com/hello"
        );

        ChatResDTO.EmoticonList result = ChatConverter.toEmoticonListFromCache(
                emoticons,
                assetUrls
        );

        assertThat(result.emoticons())
                .extracting(
                        ChatResDTO.Emoticon::id,
                        ChatResDTO.Emoticon::code,
                        ChatResDTO.Emoticon::assetUrl,
                        ChatResDTO.Emoticon::contentType,
                        ChatResDTO.Emoticon::animated
                )
                .containsExactly(
                        tuple(
                                11L,
                                "hello",
                                "https://example.com/hello",
                                "image/png",
                                false
                        ),
                        tuple(
                                21L,
                                "dance",
                                "https://example.com/dance",
                                "image/webp",
                                true
                        )
                );
    }

    @Test
    @DisplayName("캐시 목록이 비어 있으면 빈 이모티콘 응답을 반환한다")
    void toEmoticonListFromCache_EmptyCache_ReturnsEmptyList() {
        ChatResDTO.EmoticonList result = ChatConverter.toEmoticonListFromCache(
                List.of(),
                Map.of()
        );

        assertThat(result.emoticons()).isEmpty();
    }
}
