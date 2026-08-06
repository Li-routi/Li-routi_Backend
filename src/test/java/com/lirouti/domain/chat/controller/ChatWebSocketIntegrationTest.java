package com.lirouti.domain.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.lirouti.domain.chat.dto.request.ChatReqDTO;
import com.lirouti.domain.chat.dto.response.ChatResDTO;
import com.lirouti.domain.chat.enums.ChatMessageType;
import com.lirouti.domain.chat.service.command.ChatCommandService;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.global.util.JwtUtil;
import com.lirouti.global.util.RedisUtil;
import com.lirouti.global.websocket.WebSocketSessionRegistry;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DisplayName("Chat WebSocket broker 통합 테스트")
class ChatWebSocketIntegrationTest {
    private static final Long MEMBER_ID = 1L;
    private static final Long GROUP_ID = 10L;
    private static final String CHAT_DESTINATION = "/topic/groups/10/chat";
    private static final String SEND_DESTINATION = "/app/groups/10/chat/messages";

    @LocalServerPort
    private int port;

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private SimpUserRegistry simpUserRegistry;
    @Autowired
    private WebSocketSessionRegistry webSocketSessionRegistry;
    @MockitoBean
    private RedisUtil redisUtil;
    @MockitoBean
    private MemberQueryService memberQueryService;
    @MockitoBean
    private GroupValidationService groupValidationService;
    @MockitoBean
    private ChatCommandService chatCommandService;

    private WebSocketStompClient stompClient;
    private ThreadPoolTaskScheduler taskScheduler;

    @BeforeEach
    void setUp() {
        when(redisUtil.isBlackList(anyString())).thenReturn(false);

        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.initialize();

        JacksonJsonMessageConverter messageConverter = new JacksonJsonMessageConverter();
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(messageConverter);
        stompClient.setTaskScheduler(taskScheduler);
        stompClient.start();
    }

    @AfterEach
    void tearDown() {
        webSocketSessionRegistry.closeMemberSessions(MEMBER_ID);
        if (stompClient != null) {
            stompClient.stop();
        }
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
    }

    @Test
    @DisplayName("두 STOMP 세션이 같은 그룹 topic의 저장된 메시지를 수신한다")
    void sendMessage_BroadcastsToAllGroupSubscribers() throws Exception {
        // given
        ChatReqDTO.SendMessage request = new ChatReqDTO.SendMessage(
                "client-1",
                ChatMessageType.TEXT,
                "오늘 루틴 완료했어요",
                null
        );
        ChatResDTO.Message response = ChatResDTO.Message.builder()
                .id(100L)
                .clientMessageId(request.clientMessageId())
                .groupId(GROUP_ID)
                .sender(ChatResDTO.Sender.builder().memberId(MEMBER_ID).nickname("민수").build())
                .type(ChatMessageType.TEXT)
                .content(request.content())
                .createdAt(LocalDateTime.of(2026, 8, 6, 10, 0))
                .build();
        when(chatCommandService.sendMessage(
                eq(MEMBER_ID),
                eq(GROUP_ID),
                any(ChatReqDTO.SendMessage.class)
        )).thenReturn(response);

        StompSession firstSession = connect();
        StompSession secondSession = connect();
        CountDownLatch firstMessage = new CountDownLatch(1);
        CountDownLatch secondMessage = new CountDownLatch(1);
        AtomicReference<ChatResDTO.Message> firstReceived = new AtomicReference<>();
        AtomicReference<ChatResDTO.Message> secondReceived = new AtomicReference<>();

        subscribe(firstSession, firstMessage, firstReceived);
        subscribe(secondSession, secondMessage, secondReceived);
        assertThat(awaitSubscriptionCount(2)).isTrue();

        // when
        firstSession.send(SEND_DESTINATION, request);

        // then
        assertThat(firstMessage.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(secondMessage.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(firstReceived.get()).isEqualTo(response);
        assertThat(secondReceived.get()).isEqualTo(response);

        firstSession.disconnect();
        secondSession.disconnect();
    }

    @Test
    @DisplayName("회원 권한을 회수하면 모든 기기 STOMP 세션과 구독이 종료된다")
    void closeMemberSessions_MultipleSessions_DisconnectsAllSubscriptions() throws Exception {
        // given
        StompSession firstSession = connect();
        StompSession secondSession = connect();
        subscribe(firstSession, new CountDownLatch(1), new AtomicReference<>());
        subscribe(secondSession, new CountDownLatch(1), new AtomicReference<>());
        assertThat(awaitSubscriptionCount(2)).isTrue();

        // when
        int closedSessionCount = webSocketSessionRegistry.closeMemberSessions(MEMBER_ID);

        // then
        assertThat(closedSessionCount).isEqualTo(2);
        assertThat(awaitDisconnected(firstSession, secondSession)).isTrue();
        assertThat(awaitSubscriptionCount(0)).isTrue();
    }

    private StompSession connect() throws Exception {
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setBearerAuth(jwtUtil.createAccessToken(MEMBER_ID, Role.ROLE_USER));

        return stompClient.connectAsync(
                        "ws://localhost:" + port + "/ws",
                        handshakeHeaders,
                        new StompSessionHandlerAdapter() {
                        }
                )
                .get(5, TimeUnit.SECONDS);
    }

    private void subscribe(
            StompSession session,
            CountDownLatch messageReceived,
            AtomicReference<ChatResDTO.Message> receivedMessage
    ) {
        session.subscribe(
                CHAT_DESTINATION,
                new StompFrameHandler() {
                    @Override
                    public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
                        return ChatResDTO.Message.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        receivedMessage.set((ChatResDTO.Message) payload);
                        messageReceived.countDown();
                    }
                }
        );
    }

    private boolean awaitSubscriptionCount(int expectedCount) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            long subscriptionCount = simpUserRegistry.findSubscriptions(
                    subscription -> CHAT_DESTINATION.equals(subscription.getDestination())
            ).size();
            if (subscriptionCount == expectedCount) {
                return true;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        return false;
    }

    private boolean awaitDisconnected(StompSession... sessions) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            boolean allDisconnected = java.util.Arrays.stream(sessions)
                    .noneMatch(StompSession::isConnected);
            if (allDisconnected) {
                return true;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        return false;
    }
}
