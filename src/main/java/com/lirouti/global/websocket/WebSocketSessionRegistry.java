package com.lirouti.global.websocket;

import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import com.lirouti.domain.member.event.MemberWithdrawnEvent;
import com.lirouti.global.auth.CustomUserDetails;
import com.lirouti.global.util.JwtUtil;

import io.jsonwebtoken.Claims;

import lombok.extern.slf4j.Slf4j;

/**
 * 권한 회수 시 연결 자체를 종료할 수 있도록 회원별 실제 WebSocket 세션을 보관한다.
 * STOMP의 {@code SimpUserRegistry}는 사용자와 구독 조회용이며 원본 세션 종료 수단을 제공하지 않는다.
 */
@Slf4j
@Component
public class WebSocketSessionRegistry {
    private static final CloseStatus ACCESS_REVOKED =
            CloseStatus.POLICY_VIOLATION.withReason("WebSocket access revoked");
    private static final CloseStatus ACCESS_TOKEN_EXPIRED =
            CloseStatus.POLICY_VIOLATION.withReason("Access token expired");

    private final JwtUtil jwtUtil;
    private final TaskScheduler taskScheduler;
    private final ConcurrentMap<Long, ConcurrentMap<String, RegisteredSession>> sessionsByMemberId =
            new ConcurrentHashMap<>();

    // WebSocket broker도 TaskScheduler를 구성하므로 설정 조립 중 즉시 해석하지 않고 첫 예약까지 지연한다.
    public WebSocketSessionRegistry(
            JwtUtil jwtUtil,
            @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler taskScheduler
    ) {
        this.jwtUtil = jwtUtil;
        this.taskScheduler = taskScheduler;
    }

    public WebSocketHandlerDecoratorFactory decoratorFactory() {
        return this::decorate;
    }

    public int closeMemberSessions(Long memberId) {
        if (memberId == null) {
            return 0;
        }

        // 종료 callback이 다시 unregister를 호출해도 같은 세션을 중복 처리하지 않도록 먼저 분리한다.
        Map<String, RegisteredSession> sessions = sessionsByMemberId.remove(memberId);
        if (sessions == null) {
            return 0;
        }

        sessions.values().forEach(registeredSession -> {
            registeredSession.cancelExpirationTask();
            closeSession(memberId, registeredSession.session(), ACCESS_REVOKED);
        });
        return sessions.size();
    }

    /** 회원 탈퇴가 롤백된 경우 정상 세션을 끊지 않도록 커밋 이후에만 권한을 회수한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMemberWithdrawn(MemberWithdrawnEvent event) {
        int closedSessionCount = closeMemberSessions(event.memberId());
        log.info(
                "회원 탈퇴 후 WebSocket 세션을 회수했습니다. memberId={}, closedSessionCount={}",
                event.memberId(),
                closedSessionCount
        );
    }

    private WebSocketHandler decorate(WebSocketHandler delegate) {
        return new WebSocketHandlerDecorator(delegate) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                super.afterConnectionEstablished(session);
                register(session);
            }

            @Override
            public void afterConnectionClosed(
                    WebSocketSession session,
                    CloseStatus closeStatus
            ) throws Exception {
                try {
                    super.afterConnectionClosed(session, closeStatus);
                } finally {
                    unregister(session);
                }
            }
        };
    }

    private void register(WebSocketSession session) {
        Long memberId = resolveMemberId(session);
        if (memberId == null) {
            return;
        }

        Instant expiration = resolveAccessTokenExpiration(session, memberId);
        if (expiration == null) {
            // 만료 시각을 모르는 인증 세션을 등록하면 권한이 무기한 유지되므로 연결을 허용하지 않는다.
            closeSession(memberId, session, ACCESS_REVOKED);
            return;
        }

        RegisteredSession registeredSession = new RegisteredSession(session);

        // 권한 회수와 등록이 교차할 때 세션이 외부 맵에서 분리되는 틈이 없도록 회원 키 안에서 등록한다.
        sessionsByMemberId.compute(memberId, (ignored, sessions) -> {
            ConcurrentMap<String, RegisteredSession> registeredSessions = sessions == null
                    ? new ConcurrentHashMap<>()
                    : sessions;
            RegisteredSession replaced = registeredSessions.put(session.getId(), registeredSession);
            if (replaced != null) {
                replaced.cancelExpirationTask();
            }
            return registeredSessions;
        });

        ScheduledFuture<?> expirationTask;
        try {
            expirationTask = taskScheduler.schedule(
                    () -> expireSession(memberId, session.getId(), registeredSession),
                    expiration
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "WebSocket 세션 만료 작업 예약에 실패해 세션을 회수합니다. memberId={}, sessionId={}, exceptionType={}",
                    memberId,
                    session.getId(),
                    exception.getClass().getSimpleName(),
                    exception
            );
            registeredSession.cancelExpirationTask();
            expireSession(memberId, session.getId(), registeredSession);
            return;
        }
        registeredSession.setExpirationTask(expirationTask);

        if (expirationTask == null || !isRegistered(memberId, session.getId(), registeredSession)) {
            // 예약 직전 다른 권한 회수와 교차했거나 scheduler가 종료 중이면 무기한 세션을 남기지 않는다.
            registeredSession.cancelExpirationTask();
            expireSession(memberId, session.getId(), registeredSession);
        }
    }

    private void unregister(WebSocketSession session) {
        String sessionId = session.getId();
        Long memberId = resolveMemberId(session);
        if (memberId == null) {
            return;
        }

        sessionsByMemberId.computeIfPresent(memberId, (ignored, sessions) -> {
            RegisteredSession registeredSession = sessions.get(sessionId);
            if (registeredSession != null && registeredSession.session() == session) {
                sessions.remove(sessionId, registeredSession);
                registeredSession.cancelExpirationTask();
            }
            return sessions.isEmpty() ? null : sessions;
        });
    }

    private Instant resolveAccessTokenExpiration(WebSocketSession session, Long memberId) {
        String authorization = session.getHandshakeHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return null;
        }

        try {
            Claims claims = jwtUtil.getClaims(authorization.substring(7));
            Date expiration = claims.getExpiration();
            if (!"access".equals(claims.get("category", String.class))
                    || !memberId.toString().equals(claims.getSubject())
                    || expiration == null) {
                return null;
            }
            return expiration.toInstant();
        } catch (RuntimeException exception) {
            log.warn(
                    "WebSocket access token 만료 시각을 확인하지 못했습니다. memberId={}, exceptionType={}",
                    memberId,
                    exception.getClass().getSimpleName()
            );
            return null;
        }
    }

    private boolean isRegistered(
            Long memberId,
            String sessionId,
            RegisteredSession expected
    ) {
        Map<String, RegisteredSession> sessions = sessionsByMemberId.get(memberId);
        return sessions != null && sessions.get(sessionId) == expected;
    }

    private void expireSession(
            Long memberId,
            String sessionId,
            RegisteredSession expected
    ) {
        AtomicReference<RegisteredSession> expiredSession = new AtomicReference<>();
        sessionsByMemberId.computeIfPresent(memberId, (ignored, sessions) -> {
            if (sessions.remove(sessionId, expected)) {
                expiredSession.set(expected);
            }
            return sessions.isEmpty() ? null : sessions;
        });

        RegisteredSession removed = expiredSession.get();
        if (removed == null) {
            return;
        }

        removed.cancelExpirationTask();
        closeSession(memberId, removed.session(), ACCESS_TOKEN_EXPIRED);
    }

    private Long resolveMemberId(WebSocketSession session) {
        Principal principal = session.getPrincipal();
        if (!(principal instanceof Authentication authentication)
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return null;
        }
        return userDetails.getMemberId();
    }

    private void closeSession(
            Long memberId,
            WebSocketSession session,
            CloseStatus closeStatus
    ) {
        if (!session.isOpen()) {
            return;
        }

        try {
            session.close(closeStatus);
        } catch (IOException | RuntimeException exception) {
            log.warn(
                    "권한이 회수된 WebSocket 세션 종료에 실패했습니다. memberId={}, sessionId={}",
                    memberId,
                    session.getId(),
                    exception
            );
        }
    }

    private static final class RegisteredSession {
        private final WebSocketSession session;
        private final AtomicReference<ScheduledFuture<?>> expirationTask = new AtomicReference<>();

        private RegisteredSession(WebSocketSession session) {
            this.session = session;
        }

        private WebSocketSession session() {
            return session;
        }

        private void setExpirationTask(ScheduledFuture<?> task) {
            expirationTask.set(task);
        }

        private void cancelExpirationTask() {
            ScheduledFuture<?> task = expirationTask.getAndSet(null);
            if (task != null) {
                task.cancel(false);
            }
        }
    }
}
