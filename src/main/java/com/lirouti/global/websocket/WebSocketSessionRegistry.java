package com.lirouti.global.websocket;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import com.lirouti.domain.member.event.MemberWithdrawnEvent;
import com.lirouti.global.auth.CustomUserDetails;

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

    private final ConcurrentMap<Long, ConcurrentMap<String, WebSocketSession>> sessionsByMemberId =
            new ConcurrentHashMap<>();

    public WebSocketHandlerDecoratorFactory decoratorFactory() {
        return this::decorate;
    }

    public int closeMemberSessions(Long memberId) {
        if (memberId == null) {
            return 0;
        }

        // 종료 callback이 다시 unregister를 호출해도 같은 세션을 중복 처리하지 않도록 먼저 분리한다.
        Map<String, WebSocketSession> sessions = sessionsByMemberId.remove(memberId);
        if (sessions == null) {
            return 0;
        }

        sessions.values().forEach(session -> closeSession(memberId, session));
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

        // 권한 회수와 등록이 교차할 때 세션이 외부 맵에서 분리되는 틈이 없도록 회원 키 안에서 등록한다.
        sessionsByMemberId.compute(memberId, (ignored, sessions) -> {
            ConcurrentMap<String, WebSocketSession> registeredSessions = sessions == null
                    ? new ConcurrentHashMap<>()
                    : sessions;
            registeredSessions.put(session.getId(), session);
            return registeredSessions;
        });
    }

    private void unregister(WebSocketSession session) {
        Long memberId = resolveMemberId(session);
        if (memberId == null) {
            return;
        }

        sessionsByMemberId.computeIfPresent(memberId, (ignored, sessions) -> {
            sessions.remove(session.getId(), session);
            return sessions.isEmpty() ? null : sessions;
        });
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

    private void closeSession(Long memberId, WebSocketSession session) {
        if (!session.isOpen()) {
            return;
        }

        try {
            session.close(ACCESS_REVOKED);
        } catch (IOException exception) {
            log.warn(
                    "권한이 회수된 WebSocket 세션 종료에 실패했습니다. memberId={}, sessionId={}",
                    memberId,
                    session.getId(),
                    exception
            );
        }
    }
}
