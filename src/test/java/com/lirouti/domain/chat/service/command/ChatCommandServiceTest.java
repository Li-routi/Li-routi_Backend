package com.lirouti.domain.chat.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import com.lirouti.domain.chat.dto.request.ChatReqDTO;
import com.lirouti.domain.chat.dto.result.ChatSendResult;
import com.lirouti.domain.chat.dto.response.ChatResDTO;
import com.lirouti.domain.chat.entity.ChatEmoticon;
import com.lirouti.domain.chat.entity.ChatMessage;
import com.lirouti.domain.chat.enums.ChatMessageType;
import com.lirouti.domain.chat.event.ChatEmoticonCacheInvalidatedEvent;
import com.lirouti.domain.chat.exception.ChatException;
import com.lirouti.domain.chat.exception.code.error.ChatErrorCode;
import com.lirouti.domain.chat.repository.ChatEmoticonRepository;
import com.lirouti.domain.chat.repository.ChatMessageRepository;
import com.lirouti.domain.chat.repository.ChatReadRepository;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.media.service.MediaService;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatCommandService 테스트")
class ChatCommandServiceTest {
    private static final Long MEMBER_ID = 1L;
    private static final Long GROUP_ID = 10L;
    private static final Long MESSAGE_ID = 100L;
    private static final Long EMOTICON_ID = 200L;
    private static final String CLIENT_MESSAGE_ID = "client-message-1";
    private static final String EMOTICON_CODE = "BASIC_HELLO_01";
    private static final String EMOTICON_KEY =
            "chat-emoticons/2026/08/08/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.png";

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
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ChatMessage message;
    @Mock
    private Group group;
    @Mock
    private Member sender;

    @InjectMocks
    private ChatCommandService chatCommandService;

    @Test
    @DisplayName("검증과 업로드가 끝난 이모티콘 메타데이터를 활성 상태로 저장한다")
    void createEmoticonMetadata_ValidRequest_SavesActiveStaticEmoticon() {
        ChatReqDTO.RegisterEmoticon request = registerEmoticonRequest();
        when(chatEmoticonRepository.saveAndFlush(any(ChatEmoticon.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatEmoticon result = chatCommandService.createEmoticonMetadata(
                request,
                EMOTICON_KEY,
                "image/png"
        );

        assertThat(result.getCode()).isEqualTo(EMOTICON_CODE);
        assertThat(result.getAssetKey()).isEqualTo(EMOTICON_KEY);
        assertThat(result.getContentType()).isEqualTo("image/png");
        assertThat(result.getAnimated()).isFalse();
        assertThat(result.isActive()).isTrue();
        assertThat(result.getDisplayOrder()).isEqualTo(10);
        verify(eventPublisher).publishEvent(any(ChatEmoticonCacheInvalidatedEvent.class));
    }

    @Test
    @DisplayName("code unique 충돌은 채팅 중복 코드 예외로 변환한다")
    void createEmoticonMetadata_DuplicateCode_ThrowsConflict() {
        when(chatEmoticonRepository.saveAndFlush(any(ChatEmoticon.class)))
                .thenThrow(constraintViolation(
                        "lirouti.uk_chat_emoticon_code",
                        ConstraintViolationException.ConstraintKind.UNIQUE
                ));

        assertThatThrownBy(() -> chatCommandService.createEmoticonMetadata(
                registerEmoticonRequest(),
                EMOTICON_KEY,
                "image/png"
        )).isInstanceOf(ChatException.class)
                .extracting("code")
                .isEqualTo(ChatErrorCode.DUPLICATE_EMOTICON_CODE);
        verify(eventPublisher, never()).publishEvent(any(ChatEmoticonCacheInvalidatedEvent.class));
    }

    @Test
    @DisplayName("다른 DB 무결성 오류는 중복 코드로 오인하지 않는다")
    void createEmoticonMetadata_OtherConstraintViolation_RethrowsOriginal() {
        DataIntegrityViolationException exception = constraintViolation(
                "other_constraint",
                ConstraintViolationException.ConstraintKind.OTHER
        );
        when(chatEmoticonRepository.saveAndFlush(any(ChatEmoticon.class)))
                .thenThrow(exception);

        assertThatThrownBy(() -> chatCommandService.createEmoticonMetadata(
                registerEmoticonRequest(),
                EMOTICON_KEY,
                "image/png"
        )).isSameAs(exception);
        verify(eventPublisher, never()).publishEvent(any(ChatEmoticonCacheInvalidatedEvent.class));
    }

    @Test
    @DisplayName("비활성 이모티콘을 활성화하고 같은 요청을 반복해도 활성 상태를 유지한다")
    void updateEmoticonStatus_ActivateTwice_RemainsActive() {
        ChatEmoticon emoticon = emoticon(false);
        when(chatEmoticonRepository.findById(EMOTICON_ID))
                .thenReturn(Optional.of(emoticon));

        chatCommandService.updateEmoticonStatus(EMOTICON_ID, true);
        chatCommandService.updateEmoticonStatus(EMOTICON_ID, true);

        assertThat(emoticon.isActive()).isTrue();
        verify(eventPublisher).publishEvent(any(ChatEmoticonCacheInvalidatedEvent.class));
    }

    @Test
    @DisplayName("활성 이모티콘을 비활성화하고 같은 요청을 반복해도 비활성 상태를 유지한다")
    void updateEmoticonStatus_DeactivateTwice_RemainsInactive() {
        ChatEmoticon emoticon = emoticon(true);
        when(chatEmoticonRepository.findById(EMOTICON_ID))
                .thenReturn(Optional.of(emoticon));

        chatCommandService.updateEmoticonStatus(EMOTICON_ID, false);
        chatCommandService.updateEmoticonStatus(EMOTICON_ID, false);

        assertThat(emoticon.isActive()).isFalse();
        verify(eventPublisher).publishEvent(any(ChatEmoticonCacheInvalidatedEvent.class));
    }

    @Test
    @DisplayName("존재하지 않는 이모티콘의 상태는 변경하지 않는다")
    void updateEmoticonStatus_NotFound_Throws404() {
        when(chatEmoticonRepository.findById(EMOTICON_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatCommandService.updateEmoticonStatus(
                EMOTICON_ID,
                true
        )).isInstanceOf(ChatException.class)
                .extracting("code")
                .isEqualTo(ChatErrorCode.EMOTICON_NOT_FOUND);
        verify(eventPublisher, never()).publishEvent(any(ChatEmoticonCacheInvalidatedEvent.class));
    }

    @Test
    @DisplayName("TEXT 메시지를 저장하고 서버 기준 응답을 반환한다")
    void sendMessage_TextMessage_SavesAndReturnsServerMessage() {
        // given
        ChatReqDTO.SendMessage request = textRequest("오늘 루틴 완료했어요");
        givenSavedMessage(request);
        when(chatMessageRepository.findByGroupIdAndSenderIdAndClientMessageId(
                GROUP_ID, MEMBER_ID, CLIENT_MESSAGE_ID))
                .thenReturn(Optional.empty(), Optional.of(message));
        when(chatMessageRepository.insertIfAbsent(
                GROUP_ID,
                MEMBER_ID,
                CLIENT_MESSAGE_ID,
                ChatMessageType.TEXT.name(),
                request.content(),
                null
        )).thenReturn(1);

        // when
        ChatSendResult result = chatCommandService.sendMessage(
                MEMBER_ID, GROUP_ID, request);

        // then
        assertThat(result.message().id()).isEqualTo(MESSAGE_ID);
        assertThat(result.message().groupId()).isEqualTo(GROUP_ID);
        assertThat(result.message().clientMessageId()).isEqualTo(CLIENT_MESSAGE_ID);
        assertThat(result.message().type()).isEqualTo(ChatMessageType.TEXT);
        assertThat(result.message().content()).isEqualTo(request.content());
        assertThat(result.newlyCreated()).isTrue();
        InOrder authorizationOrder = inOrder(groupValidationService, chatMessageRepository);
        authorizationOrder.verify(groupValidationService).lockActiveGroupForUpdate(GROUP_ID);
        authorizationOrder.verify(groupValidationService)
                .validateActiveGroupMember(GROUP_ID, MEMBER_ID);
        authorizationOrder.verify(chatMessageRepository)
                .findByGroupIdAndSenderIdAndClientMessageId(
                        GROUP_ID,
                        MEMBER_ID,
                        CLIENT_MESSAGE_ID
                );
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
        ChatSendResult result = chatCommandService.sendMessage(
                MEMBER_ID, GROUP_ID, request);

        // then
        assertThat(result.message().id()).isEqualTo(MESSAGE_ID);
        assertThat(result.newlyCreated()).isFalse();
        verify(groupValidationService).lockActiveGroupForUpdate(GROUP_ID);
        verify(groupValidationService)
                .validateActiveGroupMember(GROUP_ID, MEMBER_ID);
        verify(chatMessageRepository, never()).insertIfAbsent(
                anyLong(), anyLong(), anyString(), anyString(), anyString(), nullable(Long.class)
        );
    }

    @Test
    @DisplayName("ACTIVE 그룹 멤버가 아니면 메시지 조회와 저장을 시도하지 않는다")
    void sendMessage_InactiveGroupMember_ThrowsAccessDenied() {
        // given
        ChatReqDTO.SendMessage request = textRequest("권한이 회수된 메시지");
        when(groupValidationService.validateActiveGroupMember(GROUP_ID, MEMBER_ID))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED));

        // when & then
        assertThatThrownBy(() -> chatCommandService.sendMessage(
                MEMBER_ID, GROUP_ID, request))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED);
        verify(groupValidationService).lockActiveGroupForUpdate(GROUP_ID);
        verifyNoInteractions(chatMessageRepository, chatEmoticonRepository, mediaService);
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

    private ChatReqDTO.RegisterEmoticon registerEmoticonRequest() {
        return new ChatReqDTO.RegisterEmoticon(
                EMOTICON_CODE,
                "image/png",
                10
        );
    }

    private ChatEmoticon emoticon(boolean active) {
        return ChatEmoticon.builder()
                .code(EMOTICON_CODE)
                .assetKey(EMOTICON_KEY)
                .contentType("image/png")
                .animated(false)
                .active(active)
                .displayOrder(10)
                .build();
    }

    private DataIntegrityViolationException constraintViolation(
            String constraintName,
            ConstraintViolationException.ConstraintKind kind
    ) {
        SQLException sqlException = new SQLException("constraint violation", "23000", 1062);
        ConstraintViolationException constraintViolation = new ConstraintViolationException(
                "constraint violation",
                sqlException,
                kind,
                constraintName
        );
        return new DataIntegrityViolationException("constraint violation", constraintViolation);
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
