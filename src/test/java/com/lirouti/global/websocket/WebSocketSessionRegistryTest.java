package com.lirouti.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import com.lirouti.domain.member.enums.Role;
import com.lirouti.global.auth.CustomUserDetails;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketSessionRegistry 테스트")
class WebSocketSessionRegistryTest {
    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;

    @Mock
    private WebSocketHandler delegate;
    @Mock
    private WebSocketSession firstSession;
    @Mock
    private WebSocketSession secondSession;
    @Mock
    private WebSocketSession otherMemberSession;

    private WebSocketSessionRegistry registry;
    private WebSocketHandler decoratedHandler;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSessionRegistry();
        decoratedHandler = registry.decoratorFactory().decorate(delegate);
    }

    @Test
    @DisplayName("회원의 모든 기기 WebSocket 세션을 정책 위반 상태로 종료한다")
    void closeMemberSessions_MultipleSessions_ClosesAllMemberSessions() throws Exception {
        // given
        givenAuthenticatedSession(firstSession, "session-1", MEMBER_ID);
        givenAuthenticatedSession(secondSession, "session-2", MEMBER_ID);
        givenAuthenticatedSession(otherMemberSession, "session-3", OTHER_MEMBER_ID);
        when(firstSession.isOpen()).thenReturn(true);
        when(secondSession.isOpen()).thenReturn(true);
        decoratedHandler.afterConnectionEstablished(firstSession);
        decoratedHandler.afterConnectionEstablished(secondSession);
        decoratedHandler.afterConnectionEstablished(otherMemberSession);

        // when
        int closedSessionCount = registry.closeMemberSessions(MEMBER_ID);

        // then
        assertThat(closedSessionCount).isEqualTo(2);
        verify(firstSession).close(argThat(this::isPolicyViolation));
        verify(secondSession).close(argThat(this::isPolicyViolation));
        verify(otherMemberSession, never()).close(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("연결이 이미 종료된 세션은 다시 회수하지 않는다")
    void afterConnectionClosed_RegisteredSession_RemovesSessionIdempotently() throws Exception {
        // given
        givenAuthenticatedSession(firstSession, "session-1", MEMBER_ID);
        decoratedHandler.afterConnectionEstablished(firstSession);
        decoratedHandler.afterConnectionClosed(firstSession, CloseStatus.NORMAL);

        // when
        int firstAttempt = registry.closeMemberSessions(MEMBER_ID);
        int secondAttempt = registry.closeMemberSessions(MEMBER_ID);

        // then
        assertThat(firstAttempt).isZero();
        assertThat(secondAttempt).isZero();
        verify(firstSession, never()).close(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("인증 회원 Principal이 없는 세션은 회수 대상에 등록하지 않는다")
    void afterConnectionEstablished_UnauthenticatedSession_DoesNotRegisterSession() throws Exception {
        // given
        when(firstSession.getPrincipal()).thenReturn(null);
        decoratedHandler.afterConnectionEstablished(firstSession);

        // when
        int closedSessionCount = registry.closeMemberSessions(MEMBER_ID);

        // then
        assertThat(closedSessionCount).isZero();
        verify(firstSession, never()).close(org.mockito.ArgumentMatchers.any());
    }

    private void givenAuthenticatedSession(
            WebSocketSession session,
            String sessionId,
            Long memberId
    ) {
        when(session.getPrincipal()).thenReturn(authentication(memberId));
        when(session.getId()).thenReturn(sessionId);
    }

    private Principal authentication(Long memberId) {
        CustomUserDetails userDetails = new CustomUserDetails(memberId, Role.ROLE_USER);
        return new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
    }

    private boolean isPolicyViolation(CloseStatus closeStatus) {
        return closeStatus != null && closeStatus.getCode() == CloseStatus.POLICY_VIOLATION.getCode();
    }
}
