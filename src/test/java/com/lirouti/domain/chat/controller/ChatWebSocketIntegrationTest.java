package com.lirouti.domain.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.lirouti.domain.chat.dto.request.ChatReqDTO;
import com.lirouti.domain.chat.dto.result.ChatSendResult;
import com.lirouti.domain.chat.dto.response.ChatResDTO;
import com.lirouti.domain.chat.enums.ChatMessageType;
import com.lirouti.domain.chat.exception.ChatException;
import com.lirouti.domain.chat.exception.code.error.ChatErrorCode;
import com.lirouti.domain.chat.service.command.ChatCommandService;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.global.auth.CustomUserDetails;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.global.util.JwtUtil;
import com.lirouti.global.util.RedisUtil;
import com.lirouti.global.websocket.WebSocketSessionRegistry;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Chat WebSocket broker 통합 테스트")
class ChatWebSocketIntegrationTest {
    private static final Long MEMBER_ID = 1L;
    private static final Long OWNER_ID = 2L;
    private static final Long GROUP_ID = 10L;
    private static final String CHAT_DESTINATION = "/topic/groups/10/chat";
    private static final String ERROR_DESTINATION = "/user/queue/errors";
    private static final String SEND_DESTINATION = "/app/groups/10/chat/messages";

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;
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
        )).thenReturn(new ChatSendResult(response, true));

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
    @DisplayName("같은 STOMP 세션의 연속 메시지는 전송 순서대로 broadcast된다")
    void sendMessage_SameSession_PreservesOrder() throws Exception {
        // given
        ChatReqDTO.SendMessage firstRequest = new ChatReqDTO.SendMessage(
                "client-1",
                ChatMessageType.TEXT,
                "첫 번째 메시지",
                null
        );
        ChatReqDTO.SendMessage secondRequest = new ChatReqDTO.SendMessage(
                "client-2",
                ChatMessageType.TEXT,
                "두 번째 메시지",
                null
        );
        ChatResDTO.Message firstResponse = ChatResDTO.Message.builder()
                .id(100L)
                .clientMessageId(firstRequest.clientMessageId())
                .groupId(GROUP_ID)
                .sender(ChatResDTO.Sender.builder().memberId(MEMBER_ID).nickname("민수").build())
                .type(ChatMessageType.TEXT)
                .content(firstRequest.content())
                .createdAt(LocalDateTime.of(2026, 8, 6, 10, 0))
                .build();
        ChatResDTO.Message secondResponse = ChatResDTO.Message.builder()
                .id(101L)
                .clientMessageId(secondRequest.clientMessageId())
                .groupId(GROUP_ID)
                .sender(ChatResDTO.Sender.builder().memberId(MEMBER_ID).nickname("민수").build())
                .type(ChatMessageType.TEXT)
                .content(secondRequest.content())
                .createdAt(LocalDateTime.of(2026, 8, 6, 10, 1))
                .build();
        when(chatCommandService.sendMessage(
                eq(MEMBER_ID),
                eq(GROUP_ID),
                any(ChatReqDTO.SendMessage.class)
        )).thenReturn(
                new ChatSendResult(firstResponse, true),
                new ChatSendResult(secondResponse, true)
        );

        StompSession session = connect();
        CountDownLatch messagesReceived = new CountDownLatch(2);
        List<String> receivedClientMessageIds = new java.util.concurrent.CopyOnWriteArrayList<>();
        session.subscribe(
                CHAT_DESTINATION,
                new StompFrameHandler() {
                    @Override
                    public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
                        return ChatResDTO.Message.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        ChatResDTO.Message message = (ChatResDTO.Message) payload;
                        receivedClientMessageIds.add(message.clientMessageId());
                        messagesReceived.countDown();
                    }
                }
        );
        assertThat(awaitSubscriptionCount(1)).isTrue();

        // when
        session.send(SEND_DESTINATION, firstRequest);
        session.send(SEND_DESTINATION, secondRequest);

        // then
        assertThat(messagesReceived.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedClientMessageIds)
                .containsExactly(firstRequest.clientMessageId(), secondRequest.clientMessageId());

        session.disconnect();
    }

    @Test
    @DisplayName("같은 clientMessageId를 일반 재전송해도 한 번만 broadcast한다")
    void sendMessage_Retry_BroadcastsOnlyOnce() throws Exception {
        // given
        ChatReqDTO.SendMessage request = new ChatReqDTO.SendMessage(
                "client-retry",
                ChatMessageType.TEXT,
                "재전송 메시지",
                null
        );
        ChatResDTO.Message response = ChatResDTO.Message.builder()
                .id(103L)
                .clientMessageId(request.clientMessageId())
                .groupId(GROUP_ID)
                .type(ChatMessageType.TEXT)
                .content(request.content())
                .createdAt(LocalDateTime.of(2026, 8, 6, 10, 3))
                .build();
        when(chatCommandService.sendMessage(
                eq(MEMBER_ID),
                eq(GROUP_ID),
                eq(request)
        )).thenReturn(
                new ChatSendResult(response, true),
                new ChatSendResult(response, false)
        );

        StompSession session = connect();
        CountDownLatch messageReceived = new CountDownLatch(1);
        AtomicInteger receivedCount = new AtomicInteger();
        subscribeCounting(session, messageReceived, receivedCount);
        assertThat(awaitSubscriptionCount(1)).isTrue();

        // when
        session.send(SEND_DESTINATION, request);
        session.send(SEND_DESTINATION, request);

        // then
        assertThat(messageReceived.await(5, TimeUnit.SECONDS)).isTrue();
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(300));
        assertThat(receivedCount).hasValue(1);
        verify(chatCommandService, timeout(5000).times(2))
                .sendMessage(MEMBER_ID, GROUP_ID, request);

        session.disconnect();
    }

    @Test
    @DisplayName("동일 메시지 동시 재전송도 한 번만 broadcast한다")
    void sendMessage_ConcurrentRetry_BroadcastsOnlyOnce() throws Exception {
        // given
        ChatReqDTO.SendMessage request = new ChatReqDTO.SendMessage(
                "client-concurrent-retry",
                ChatMessageType.TEXT,
                "동시 재전송 메시지",
                null
        );
        ChatResDTO.Message response = ChatResDTO.Message.builder()
                .id(104L)
                .clientMessageId(request.clientMessageId())
                .groupId(GROUP_ID)
                .type(ChatMessageType.TEXT)
                .content(request.content())
                .createdAt(LocalDateTime.of(2026, 8, 6, 10, 4))
                .build();
        CountDownLatch serviceCalls = new CountDownLatch(2);
        CountDownLatch releaseService = new CountDownLatch(1);
        AtomicInteger invocationCount = new AtomicInteger();
        when(chatCommandService.sendMessage(
                eq(MEMBER_ID),
                eq(GROUP_ID),
                eq(request)
        )).thenAnswer(invocation -> {
            int invocationNumber = invocationCount.incrementAndGet();
            serviceCalls.countDown();
            assertThat(releaseService.await(5, TimeUnit.SECONDS)).isTrue();
            return new ChatSendResult(
                    response,
                    invocationNumber == 1
            );
        });

        StompSession firstSession = connect();
        StompSession secondSession = connect();
        CountDownLatch messagesReceived = new CountDownLatch(2);
        AtomicInteger receivedCount = new AtomicInteger();
        subscribeCounting(firstSession, messagesReceived, receivedCount);
        subscribeCounting(secondSession, messagesReceived, receivedCount);
        assertThat(awaitSubscriptionCount(2)).isTrue();

        // when
        firstSession.send(SEND_DESTINATION, request);
        secondSession.send(SEND_DESTINATION, request);
        assertThat(serviceCalls.await(5, TimeUnit.SECONDS)).isTrue();
        releaseService.countDown();

        // then
        assertThat(messagesReceived.await(5, TimeUnit.SECONDS)).isTrue();
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(300));
        assertThat(receivedCount).hasValue(2);
        assertThat(invocationCount).hasValue(2);

        firstSession.disconnect();
        secondSession.disconnect();
    }

    @Test
    @DisplayName("복구 가능한 도메인 오류를 오류 destination으로 받고 같은 세션을 유지한다")
    void sendMessage_RecoverableDomainError_KeepsSessionConnected() throws Exception {
        // given
        ChatReqDTO.SendMessage failedRequest = new ChatReqDTO.SendMessage(
                "client-error",
                ChatMessageType.TEXT,
                "실패 메시지",
                null
        );
        ChatReqDTO.SendMessage retryRequest = new ChatReqDTO.SendMessage(
                "client-retry",
                ChatMessageType.TEXT,
                "재시도 메시지",
                null
        );
        ChatResDTO.Message retryResponse = ChatResDTO.Message.builder()
                .id(102L)
                .clientMessageId(retryRequest.clientMessageId())
                .groupId(GROUP_ID)
                .sender(ChatResDTO.Sender.builder().memberId(MEMBER_ID).nickname("민수").build())
                .type(ChatMessageType.TEXT)
                .content(retryRequest.content())
                .createdAt(LocalDateTime.of(2026, 8, 6, 10, 2))
                .build();
        when(chatCommandService.sendMessage(
                eq(MEMBER_ID),
                eq(GROUP_ID),
                eq(failedRequest)
        )).thenThrow(new ChatException(ChatErrorCode.MESSAGE_CONTENT_INVALID));
        when(chatCommandService.sendMessage(
                eq(MEMBER_ID),
                eq(GROUP_ID),
                eq(retryRequest)
        )).thenReturn(new ChatSendResult(retryResponse, true));

        StompSession session = connect();
        CountDownLatch errorReceived = new CountDownLatch(1);
        CountDownLatch retryMessageReceived = new CountDownLatch(1);
        AtomicReference<Map<String, Object>> receivedError = new AtomicReference<>();
        AtomicReference<ChatResDTO.Message> receivedRetryMessage = new AtomicReference<>();
        session.subscribe(
                ERROR_DESTINATION,
                new StompFrameHandler() {
                    @Override
                    public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
                        return Map.class;
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public void handleFrame(StompHeaders headers, Object payload) {
                        receivedError.set((Map<String, Object>) payload);
                        errorReceived.countDown();
                    }
                }
        );
        subscribe(session, retryMessageReceived, receivedRetryMessage);
        assertThat(awaitSubscriptionCount(ERROR_DESTINATION, 1)).isTrue();
        assertThat(awaitSubscriptionCount(CHAT_DESTINATION, 1)).isTrue();

        // when
        session.send(SEND_DESTINATION, failedRequest);

        // then
        assertThat(errorReceived.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(session.isConnected()).isTrue();
        assertThat(receivedError.get()).containsEntry("code", ChatErrorCode.MESSAGE_CONTENT_INVALID.getCode());
        assertThat(receivedError.get()).containsEntry("clientMessageId", failedRequest.clientMessageId());

        session.send(SEND_DESTINATION, retryRequest);
        assertThat(retryMessageReceived.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedRetryMessage.get()).isEqualTo(retryResponse);

        session.disconnect();
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

    @Test
    @DisplayName("그룹 탈퇴 커밋 후 실제 세션을 종료하고 재연결 구독을 차단한다")
    void leaveGroup_AfterCommit_ClosesSessionsAndRejectsReconnect() throws Exception {
        // given
        GroupMember membership = mock(GroupMember.class);
        when(groupValidationService.lockActiveGroupForUpdate(GROUP_ID)).thenReturn(null);
        when(groupValidationService.validateActiveGroupMember(GROUP_ID, MEMBER_ID))
                .thenReturn(membership);

        StompSession firstSession = connect();
        StompSession secondSession = connect();
        subscribe(firstSession, new CountDownLatch(1), new AtomicReference<>());
        subscribe(secondSession, new CountDownLatch(1), new AtomicReference<>());
        assertThat(awaitSubscriptionCount(2)).isTrue();

        // when
        mockMvc.perform(delete("/api/groups/{groupId}/leave", GROUP_ID)
                        .with(user(new CustomUserDetails(MEMBER_ID, Role.ROLE_USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GROUP200_6"));

        // then
        verify(membership).leave();
        assertThat(awaitDisconnected(firstSession, secondSession)).isTrue();
        assertThat(awaitSubscriptionCount(0)).isTrue();

        clearInvocations(groupValidationService);
        when(groupValidationService.validateActiveGroupMember(GROUP_ID, MEMBER_ID))
                .thenThrow(new GroupException(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED));
        StompSession reconnectedSession = connect();
        reconnectedSession.subscribe(
                CHAT_DESTINATION,
                new StompFrameHandler() {
                    @Override
                    public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
                        return ChatResDTO.Message.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                    }
                }
        );
        verify(groupValidationService, timeout(5_000))
                .validateActiveGroupMember(GROUP_ID, MEMBER_ID);
        assertThat(awaitSubscriptionCount(0)).isTrue();
        reconnectedSession.disconnect();
    }

    @Test
    @DisplayName("그룹 강퇴 커밋 후 대상 회원의 모든 실제 세션을 종료한다")
    void kickMember_AfterCommit_ClosesTargetSessions() throws Exception {
        // given
        GroupMember ownerMembership = mock(GroupMember.class);
        GroupMember targetMembership = mock(GroupMember.class);
        when(groupValidationService.lockActiveGroupForUpdate(GROUP_ID)).thenReturn(null);
        when(groupValidationService.validateGroupOwner(GROUP_ID, OWNER_ID))
                .thenReturn(ownerMembership);
        when(groupValidationService.validateActiveGroupMember(GROUP_ID, MEMBER_ID))
                .thenReturn(targetMembership);

        StompSession firstSession = connect();
        StompSession secondSession = connect();
        subscribe(firstSession, new CountDownLatch(1), new AtomicReference<>());
        subscribe(secondSession, new CountDownLatch(1), new AtomicReference<>());
        assertThat(awaitSubscriptionCount(2)).isTrue();

        // when
        mockMvc.perform(delete(
                                "/api/groups/{groupId}/members/{targetMemberId}",
                                GROUP_ID,
                                MEMBER_ID
                        )
                        .with(user(new CustomUserDetails(OWNER_ID, Role.ROLE_USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GROUP200_9"));

        // then
        verify(targetMembership).kick();
        assertThat(awaitDisconnected(firstSession, secondSession)).isTrue();
        assertThat(awaitSubscriptionCount(0)).isTrue();
    }

    private StompSession connect() throws Exception {
        return connect(new StompSessionHandlerAdapter() {
        });
    }

    private StompSession connect(StompSessionHandlerAdapter sessionHandler) throws Exception {
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setBearerAuth(jwtUtil.createAccessToken(MEMBER_ID, Role.ROLE_USER));

        return stompClient.connectAsync(
                        "ws://localhost:" + port + "/ws",
                        handshakeHeaders,
                        sessionHandler
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

    private void subscribeCounting(
            StompSession session,
            CountDownLatch messagesReceived,
            AtomicInteger receivedCount
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
                        receivedCount.incrementAndGet();
                        messagesReceived.countDown();
                    }
                }
        );
    }

    private boolean awaitSubscriptionCount(int expectedCount) {
        return awaitSubscriptionCount(CHAT_DESTINATION, expectedCount);
    }

    private boolean awaitSubscriptionCount(String destination, int expectedCount) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            long subscriptionCount = simpUserRegistry.findSubscriptions(
                    subscription -> destination.equals(subscription.getDestination())
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
