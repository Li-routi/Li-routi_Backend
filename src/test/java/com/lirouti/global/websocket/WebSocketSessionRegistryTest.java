package com.lirouti.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import com.lirouti.domain.member.enums.Role;
import com.lirouti.global.auth.CustomUserDetails;
import com.lirouti.global.util.JwtUtil;

import io.jsonwebtoken.Claims;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketSessionRegistry 테스트")
class WebSocketSessionRegistryTest {
    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final Instant DEFAULT_EXPIRATION = Instant.parse("2030-08-06T06:00:00Z");

    @Mock
    private WebSocketHandler delegate;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private TaskScheduler taskScheduler;
    @Mock
    private ScheduledFuture<?> expirationTask;
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
        registry = new WebSocketSessionRegistry(jwtUtil, taskScheduler);
        decoratedHandler = registry.decoratorFactory().decorate(delegate);
    }

    @Test
    @DisplayName("회원의 모든 기기 WebSocket 세션을 정책 위반 상태로 종료한다")
    void closeMemberSessions_MultipleSessions_ClosesAllMemberSessions() throws Exception {
        // given
        givenExpirationScheduling();
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
        givenExpirationScheduling();
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

    @Test
    @DisplayName("access token이 만료되면 해당 세션만 종료하고 새 토큰 세션은 유지한다")
    void accessTokenExpiration_OldSession_ClosesOnlyExpiredSession() throws Exception {
        // given
        Instant firstExpiration = Instant.parse("2030-08-06T06:00:00Z");
        Instant secondExpiration = Instant.parse("2030-08-06T06:05:00Z");
        ScheduledFuture<?> firstExpirationTask = mock(ScheduledFuture.class);
        ScheduledFuture<?> secondExpirationTask = mock(ScheduledFuture.class);
        doReturn(firstExpirationTask)
                .when(taskScheduler).schedule(any(Runnable.class), eq(firstExpiration));
        doReturn(secondExpirationTask)
                .when(taskScheduler).schedule(any(Runnable.class), eq(secondExpiration));
        givenAuthenticatedSession(
                firstSession,
                "session-1",
                MEMBER_ID,
                "old-access-token",
                firstExpiration
        );
        givenAuthenticatedSession(
                secondSession,
                "session-2",
                MEMBER_ID,
                "new-access-token",
                secondExpiration
        );
        when(firstSession.isOpen()).thenReturn(true);
        when(secondSession.isOpen()).thenReturn(true);
        decoratedHandler.afterConnectionEstablished(firstSession);
        decoratedHandler.afterConnectionEstablished(secondSession);

        ArgumentCaptor<Runnable> expirationCallbacks = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Instant> expirationInstants = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler, times(2)).schedule(
                expirationCallbacks.capture(),
                expirationInstants.capture()
        );
        assertThat(expirationInstants.getAllValues())
                .containsExactly(firstExpiration, secondExpiration);

        // when
        expirationCallbacks.getAllValues().getFirst().run();

        // then
        verify(firstSession).close(argThat(this::isAccessTokenExpired));
        verify(secondSession, never()).close(any());
        assertThat(registry.closeMemberSessions(MEMBER_ID)).isEqualTo(1);
        verify(secondSession).close(argThat(this::isPolicyViolation));
    }

    @Test
    @DisplayName("인증 세션의 access token 만료 시각을 확인할 수 없으면 등록하지 않는다")
    void afterConnectionEstablished_ExpirationUnavailable_ClosesSession() throws Exception {
        // given
        when(firstSession.getPrincipal()).thenReturn(authentication(MEMBER_ID));
        when(firstSession.getHandshakeHeaders()).thenReturn(HttpHeaders.EMPTY);
        when(firstSession.isOpen()).thenReturn(true);

        // when
        decoratedHandler.afterConnectionEstablished(firstSession);

        // then
        verify(firstSession).close(argThat(this::isPolicyViolation));
        assertThat(registry.closeMemberSessions(MEMBER_ID)).isZero();
    }

    private void givenAuthenticatedSession(
            WebSocketSession session,
            String sessionId,
            Long memberId
    ) {
        givenAuthenticatedSession(
                session,
                sessionId,
                memberId,
                "access-token-" + sessionId,
                DEFAULT_EXPIRATION
        );
    }

    private void givenAuthenticatedSession(
            WebSocketSession session,
            String sessionId,
            Long memberId,
            String accessToken,
            Instant expiration
    ) {
        HttpHeaders handshakeHeaders = new HttpHeaders();
        handshakeHeaders.setBearerAuth(accessToken);
        Claims claims = mock(Claims.class);

        when(session.getPrincipal()).thenReturn(authentication(memberId));
        when(session.getId()).thenReturn(sessionId);
        when(session.getHandshakeHeaders()).thenReturn(handshakeHeaders);
        when(jwtUtil.getClaims(accessToken)).thenReturn(claims);
        when(claims.getExpiration()).thenReturn(Date.from(expiration));
        when(claims.get("category", String.class)).thenReturn("access");
        when(claims.getSubject()).thenReturn(memberId.toString());
    }

    private void givenExpirationScheduling() {
        doReturn(expirationTask)
                .when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
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

    private boolean isAccessTokenExpired(CloseStatus closeStatus) {
        return isPolicyViolation(closeStatus)
                && "Access token expired".equals(closeStatus.getReason());
    }
}
