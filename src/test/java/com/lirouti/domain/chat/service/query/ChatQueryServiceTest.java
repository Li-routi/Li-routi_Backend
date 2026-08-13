package com.lirouti.domain.chat.service.query;

import com.lirouti.domain.chat.cache.ChatEmoticonCacheReader;
import com.lirouti.domain.chat.cache.ChatEmoticonCacheReader.CachedEmoticon;
import com.lirouti.domain.chat.dto.response.ChatResDTO;
import com.lirouti.domain.chat.repository.ChatEmoticonRepository;
import com.lirouti.domain.chat.repository.ChatMessageRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.service.query.MemberQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatQueryService 테스트")
class ChatQueryServiceTest {
    private static final Long MEMBER_ID = 1L;

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatEmoticonRepository chatEmoticonRepository;
    @Mock
    private ChatEmoticonCacheReader chatEmoticonCacheReader;
    @Mock
    private GroupValidationService groupValidationService;
    @Mock
    private MemberQueryService memberQueryService;
    @Mock
    private MediaService mediaService;

    @InjectMocks
    private ChatQueryService chatQueryService;

    @Test
    @DisplayName("활성 회원 검증 후 캐시 목록을 사용하고 URL은 요청마다 다시 발급한다")
    void getEmoticons_ActiveMember_UsesCacheAndResolvesUrlsPerRequest() {
        List<CachedEmoticon> cachedEmoticons = cachedEmoticons();
        when(chatEmoticonCacheReader.getActive()).thenReturn(cachedEmoticons);
        when(mediaService.resolveViewUrl("chat-emoticons/hello.png", MediaPurpose.CHAT_EMOTICON))
                .thenReturn("https://example.com/hello");
        when(mediaService.resolveViewUrl("chat-emoticons/dance.webp", MediaPurpose.CHAT_EMOTICON))
                .thenReturn("https://example.com/dance");

        ChatResDTO.EmoticonList first = chatQueryService.getEmoticons(MEMBER_ID);
        ChatResDTO.EmoticonList second = chatQueryService.getEmoticons(MEMBER_ID);

        assertThat(first.emoticons())
                .extracting(
                        ChatResDTO.Emoticon::id,
                        ChatResDTO.Emoticon::assetUrl
                )
                .containsExactly(
                        tuple(11L, "https://example.com/hello"),
                        tuple(21L, "https://example.com/dance")
                );
        assertThat(second.emoticons()).isEqualTo(first.emoticons());

        InOrder order = inOrder(memberQueryService, chatEmoticonCacheReader, mediaService);
        order.verify(memberQueryService).getActiveMember(MEMBER_ID);
        order.verify(chatEmoticonCacheReader).getActive();
        order.verify(mediaService).resolveViewUrl(
                "chat-emoticons/hello.png",
                MediaPurpose.CHAT_EMOTICON
        );
        order.verify(mediaService).resolveViewUrl(
                "chat-emoticons/dance.webp",
                MediaPurpose.CHAT_EMOTICON
        );
        order.verify(memberQueryService).getActiveMember(MEMBER_ID);
        order.verify(chatEmoticonCacheReader).getActive();
        verify(mediaService, times(2)).resolveViewUrl(
                "chat-emoticons/hello.png",
                MediaPurpose.CHAT_EMOTICON
        );
        verify(mediaService, times(2)).resolveViewUrl(
                "chat-emoticons/dance.webp",
                MediaPurpose.CHAT_EMOTICON
        );
        verify(chatEmoticonRepository, never()).findAllByActiveTrueOrderByDisplayOrderAscIdAsc();
    }

    @Test
    @DisplayName("캐시 목록이 비어 있으면 URL 발급 없이 빈 응답을 반환한다")
    void getEmoticons_EmptyCache_ReturnsEmptyListWithoutResolvingUrls() {
        when(chatEmoticonCacheReader.getActive()).thenReturn(List.of());

        ChatResDTO.EmoticonList result = chatQueryService.getEmoticons(MEMBER_ID);

        assertThat(result.emoticons()).isEmpty();
        verify(memberQueryService).getActiveMember(MEMBER_ID);
        verify(chatEmoticonCacheReader).getActive();
        verifyNoMediaUrlResolution();
    }

    private List<CachedEmoticon> cachedEmoticons() {
        return List.of(
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
    }

    private void verifyNoMediaUrlResolution() {
        verify(mediaService, never()).resolveViewUrl(
                anyString(),
                eq(MediaPurpose.CHAT_EMOTICON)
        );
    }
}
