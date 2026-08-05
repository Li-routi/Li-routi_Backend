package com.lirouti.domain.chat.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lirouti.domain.chat.dto.request.ChatReqDTO;
import com.lirouti.domain.chat.dto.response.ChatResDTO;
import com.lirouti.domain.chat.entity.ChatMessage;
import com.lirouti.domain.chat.enums.ChatMessageType;
import com.lirouti.domain.chat.exception.ChatException;
import com.lirouti.domain.chat.exception.code.error.ChatErrorCode;
import com.lirouti.domain.chat.repository.ChatEmoticonRepository;
import com.lirouti.domain.chat.repository.ChatMessageRepository;
import com.lirouti.domain.chat.repository.ChatReadRepository;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.media.service.MediaService;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatCommandService 테스트")
class ChatCommandServiceTest {
    private static final Long MEMBER_ID = 1L;
    private static final Long GROUP_ID = 10L;
    private static final Long MESSAGE_ID = 100L;
    private static final String CLIENT_MESSAGE_ID = "client-message-1";

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatEmoticonRepository chatEmoticonRepository;
    @Mock
    private ChatReadRepository chatReadRepository;
    @Mock
    private GroupValidationService groupValidationService;
    @Mock
    private MediaService mediaService;
    @Mock
    private ChatMessage message;
    @Mock
    private Group group;
    @Mock
    private Member sender;

    @InjectMocks
    private ChatCommandService chatCommandService;

    @Test
    @DisplayName("TEXT 메시지를 저장하고 서버 기준 응답을 반환한다")
    void sendMessage_TextMessage_SavesAndReturnsServerMessage() {
        // given
        ChatReqDTO.SendMessage request = textRequest("오늘 루틴 완료했어요");
        givenSavedMessage(request);
        when(chatMessageRepository.findByGroupIdAndSenderIdAndClientMessageId(
                GROUP_ID, MEMBER_ID, CLIENT_MESSAGE_ID))
                .thenReturn(Optional.empty(), Optional.of(message));

        // when
        ChatResDTO.Message result = chatCommandService.sendMessage(
                MEMBER_ID, GROUP_ID, request);

        // then
        assertThat(result.id()).isEqualTo(MESSAGE_ID);
        assertThat(result.groupId()).isEqualTo(GROUP_ID);
        assertThat(result.clientMessageId()).isEqualTo(CLIENT_MESSAGE_ID);
        assertThat(result.type()).isEqualTo(ChatMessageType.TEXT);
        assertThat(result.content()).isEqualTo(request.content());
        verify(chatMessageRepository).insertIfAbsent(
                GROUP_ID,
                MEMBER_ID,
                CLIENT_MESSAGE_ID,
                ChatMessageType.TEXT.name(),
                request.content(),
                null
        );
        verify(chatEmoticonRepository, never()).findByCodeAndActiveTrue(anyString());
    }

    @Test
    @DisplayName("동일한 clientMessageId와 payload는 기존 메시지를 반환하고 다시 저장하지 않는다")
    void sendMessage_SameClientMessageIdAndPayload_ReturnsExistingMessage() {
        // given
        ChatReqDTO.SendMessage request = textRequest("재전송 메시지");
        givenSavedMessage(request);
        when(chatMessageRepository.findByGroupIdAndSenderIdAndClientMessageId(
                GROUP_ID, MEMBER_ID, CLIENT_MESSAGE_ID))
                .thenReturn(Optional.of(message));

        // when
        ChatResDTO.Message result = chatCommandService.sendMessage(
                MEMBER_ID, GROUP_ID, request);

        // then
        assertThat(result.id()).isEqualTo(MESSAGE_ID);
        verify(chatMessageRepository, never()).insertIfAbsent(
                anyLong(), anyLong(), anyString(), anyString(), anyString(), nullable(Long.class)
        );
    }

    @Test
    @DisplayName("동일한 clientMessageId에 다른 payload를 보내면 거부한다")
    void sendMessage_SameClientMessageIdWithDifferentPayload_ThrowsInvalidClientMessageId() {
        // given
        ChatReqDTO.SendMessage request = textRequest("새로운 payload");
        when(message.getMessageType()).thenReturn(ChatMessageType.TEXT);
        when(message.getContent()).thenReturn("기존 payload");
        when(chatMessageRepository.findByGroupIdAndSenderIdAndClientMessageId(
                GROUP_ID, MEMBER_ID, CLIENT_MESSAGE_ID))
                .thenReturn(Optional.of(message));

        // when & then
        assertThatThrownBy(() -> chatCommandService.sendMessage(
                MEMBER_ID, GROUP_ID, request))
                .isInstanceOf(ChatException.class)
                .extracting("code")
                .isEqualTo(ChatErrorCode.CLIENT_MESSAGE_ID_INVALID);
        verify(chatMessageRepository, never()).insertIfAbsent(
                anyLong(), anyLong(), anyString(), anyString(), anyString(), nullable(Long.class)
        );
    }

    @Test
    @DisplayName("TEXT 메시지에 이모티콘 코드를 함께 보내면 거부한다")
    void sendMessage_TextMessageWithEmoticonCode_ThrowsInvalidMessageType() {
        // given
        ChatReqDTO.SendMessage request = new ChatReqDTO.SendMessage(
                CLIENT_MESSAGE_ID,
                ChatMessageType.TEXT,
                "텍스트",
                "BASIC_HELLO_01"
        );

        // when & then
        assertThatThrownBy(() -> chatCommandService.sendMessage(
                MEMBER_ID, GROUP_ID, request))
                .isInstanceOf(ChatException.class)
                .extracting("code")
                .isEqualTo(ChatErrorCode.MESSAGE_TYPE_INVALID);
        verify(chatMessageRepository, never()).findByGroupIdAndSenderIdAndClientMessageId(
                anyLong(), anyLong(), anyString()
        );
    }

    private ChatReqDTO.SendMessage textRequest(String content) {
        return new ChatReqDTO.SendMessage(
                CLIENT_MESSAGE_ID,
                ChatMessageType.TEXT,
                content,
                null
        );
    }

    private void givenSavedMessage(ChatReqDTO.SendMessage request) {
        when(message.getId()).thenReturn(MESSAGE_ID);
        when(message.getGroup()).thenReturn(group);
        when(message.getSender()).thenReturn(sender);
        when(message.getMessageType()).thenReturn(request.type());
        when(message.getContent()).thenReturn(request.content());
        when(message.getEmoticonId()).thenReturn(null);
        when(message.getClientMessageId()).thenReturn(CLIENT_MESSAGE_ID);
        when(message.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 8, 5, 12, 0));
        when(group.getId()).thenReturn(GROUP_ID);
        when(sender.getId()).thenReturn(MEMBER_ID);
        when(sender.getNickname()).thenReturn("채팅 사용자");
    }
}
