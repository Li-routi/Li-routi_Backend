package com.lirouti.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.global.auth.CustomUserDetails;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketAuthInterceptor 테스트")
class WebSocketAuthInterceptorTest {
    private static final Long MEMBER_ID = 1L;
    private static final Long GROUP_ID = 10L;

    @Mock
    private MemberQueryService memberQueryService;
    @Mock
    private GroupValidationService groupValidationService;
    @Mock
    private MessageChannel channel;

    private WebSocketAuthInterceptor interceptor;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthInterceptor(
                memberQueryService,
                groupValidationService
        );
        CustomUserDetails userDetails = new CustomUserDetails(MEMBER_ID, Role.ROLE_USER);
        authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
    }

    @Test
    @DisplayName("CONNECT 시 인증 회원의 활성 상태를 확인한다")
    void preSend_Connect_ValidatesActiveMember() {
        // given
        Message<?> message = stompMessage(StompCommand.CONNECT, null, authentication);

        // when
        Message<?> result = interceptor.preSend(message, channel);

        // then
        assertThat(result).isSameAs(message);
        verify(memberQueryService).getActiveMember(MEMBER_ID);
        verifyNoInteractions(groupValidationService);
    }

    @Test
    @DisplayName("SUBSCRIBE 시 destination 그룹의 ACTIVE 멤버십을 확인한다")
    void preSend_Subscribe_ValidatesDestinationGroupMembership() {
        // given
        Message<?> message = stompMessage(
                StompCommand.SUBSCRIBE,
                "/topic/groups/" + GROUP_ID + "/chat",
                authentication
        );

        // when
        Message<?> result = interceptor.preSend(message, channel);

        // then
        assertThat(result).isSameAs(message);
        verify(memberQueryService).getActiveMember(MEMBER_ID);
        verify(groupValidationService).validateActiveGroupMember(GROUP_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("SEND 시 채팅 메시지 destination 그룹의 ACTIVE 멤버십을 확인한다")
    void preSend_Send_ValidatesDestinationGroupMembership() {
        // given
        Message<?> message = stompMessage(
                StompCommand.SEND,
                "/app/groups/" + GROUP_ID + "/chat/messages",
                authentication
        );

        // when
        Message<?> result = interceptor.preSend(message, channel);

        // then
        assertThat(result).isSameAs(message);
        verify(memberQueryService).getActiveMember(MEMBER_ID);
        verify(groupValidationService).validateActiveGroupMember(GROUP_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("인증 정보가 없는 CONNECT는 거부한다")
    void preSend_ConnectWithoutAuthentication_ThrowsMessagingException() {
        // given
        Message<?> message = stompMessage(StompCommand.CONNECT, null, null);

        // when & then
        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessagingException.class);
        verifyNoInteractions(memberQueryService, groupValidationService);
    }

    @Test
    @DisplayName("채팅이 아닌 destination으로 구독하면 거부한다")
    void preSend_SubscribeToInvalidDestination_ThrowsMessagingException() {
        // given
        Message<?> message = stompMessage(
                StompCommand.SUBSCRIBE,
                "/topic/groups/" + GROUP_ID + "/members",
                authentication
        );

        // when & then
        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessagingException.class);
        verifyNoInteractions(memberQueryService, groupValidationService);
    }

    private Message<?> stompMessage(
            StompCommand command,
            String destination,
            Authentication user
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(user);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
