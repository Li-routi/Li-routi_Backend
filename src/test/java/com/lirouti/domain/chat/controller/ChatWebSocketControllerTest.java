package com.lirouti.domain.chat.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.lirouti.domain.chat.dto.request.ChatReqDTO;
import com.lirouti.domain.chat.dto.response.ChatResDTO;
import com.lirouti.domain.chat.enums.ChatMessageType;
import com.lirouti.domain.chat.service.command.ChatCommandService;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.global.auth.CustomUserDetails;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatWebSocketController 테스트")
class ChatWebSocketControllerTest {
    private static final Long MEMBER_ID = 1L;
    private static final Long GROUP_ID = 10L;
    private static final String CHAT_DESTINATION = "/topic/groups/10/chat";

    @Mock
    private ChatCommandService chatCommandService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ChatWebSocketController controller;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        controller = new ChatWebSocketController(chatCommandService, messagingTemplate);
        CustomUserDetails userDetails = new CustomUserDetails(MEMBER_ID, Role.ROLE_USER);
        authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
    }

    @Test
    @DisplayName("저장 성공 후 서버 응답을 그룹 채팅 topic으로 broadcast한다")
    void sendMessage_Success_BroadcastsSavedMessage() {
        // given
        ChatReqDTO.SendMessage request = new ChatReqDTO.SendMessage(
                "client-1",
                ChatMessageType.TEXT,
                "오늘 루틴 완료했어요",
                null
        );
        ChatResDTO.Message response = ChatResDTO.Message.builder()
                .id(100L)
                .clientMessageId("client-1")
                .groupId(GROUP_ID)
                .type(ChatMessageType.TEXT)
                .content("오늘 루틴 완료했어요")
                .createdAt(LocalDateTime.of(2026, 8, 5, 23, 0))
                .build();
        when(chatCommandService.sendMessage(MEMBER_ID, GROUP_ID, request)).thenReturn(response);

        // when
        controller.sendMessage(GROUP_ID, request, authentication);

        // then
        verify(chatCommandService).sendMessage(MEMBER_ID, GROUP_ID, request);
        verify(messagingTemplate).convertAndSend(CHAT_DESTINATION, response);
    }

    @Test
    @DisplayName("저장 서비스가 실패하면 broadcast하지 않는다")
    void sendMessage_ServiceFailure_DoesNotBroadcast() {
        // given
        ChatReqDTO.SendMessage request = new ChatReqDTO.SendMessage(
                "client-2",
                ChatMessageType.TEXT,
                "전송 실패 메시지",
                null
        );
        RuntimeException failure = new RuntimeException("save failed");
        when(chatCommandService.sendMessage(MEMBER_ID, GROUP_ID, request)).thenThrow(failure);

        // when & then
        assertThatThrownBy(() -> controller.sendMessage(GROUP_ID, request, authentication))
                .isSameAs(failure);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("인증 정보가 없는 요청은 service와 broadcast 모두 호출하지 않는다")
    void sendMessage_WithoutAuthentication_ThrowsMessagingException() {
        // given
        ChatReqDTO.SendMessage request = new ChatReqDTO.SendMessage(
                "client-3",
                ChatMessageType.TEXT,
                "인증 없는 메시지",
                null
        );

        // when & then
        assertThatThrownBy(() -> controller.sendMessage(GROUP_ID, request, null))
                .isInstanceOf(MessagingException.class);
        verifyNoInteractions(chatCommandService, messagingTemplate);
    }
}
