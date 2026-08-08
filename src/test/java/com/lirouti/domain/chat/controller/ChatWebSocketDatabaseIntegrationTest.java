package com.lirouti.domain.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
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
import com.lirouti.domain.chat.entity.ChatEmoticon;
import com.lirouti.domain.chat.entity.ChatMessage;
import com.lirouti.domain.chat.enums.ChatMessageType;
import com.lirouti.domain.chat.repository.ChatEmoticonRepository;
import com.lirouti.domain.chat.repository.ChatMessageRepository;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.global.util.JwtUtil;
import com.lirouti.global.util.RedisUtil;
import com.lirouti.global.websocket.WebSocketSessionRegistry;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("채팅 WebSocket DB 통합 테스트")
class ChatWebSocketDatabaseIntegrationTest {
    private static final String CHAT_DESTINATION_FORMAT = "/topic/groups/%d/chat";
    private static final String SEND_DESTINATION_FORMAT = "/app/groups/%d/chat/messages";

    @LocalServerPort
    private int port;

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private GroupMemberRepository groupMemberRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private ChatEmoticonRepository chatEmoticonRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SimpUserRegistry simpUserRegistry;
    @Autowired
    private WebSocketSessionRegistry webSocketSessionRegistry;

    @MockitoBean
    private RedisUtil redisUtil;
    @MockitoBean
    private MediaService mediaService;

    private WebSocketStompClient stompClient;
    private ThreadPoolTaskScheduler taskScheduler;
    private Long groupId;
    private Long memberId;
    private Long emoticonId;

    @BeforeEach
    void setUp() {
        when(redisUtil.isBlackList(anyString())).thenReturn(false);

        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.initialize();

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        stompClient.setTaskScheduler(taskScheduler);
        stompClient.start();
    }

    @AfterEach
    void tearDown() {
        if (memberId != null) {
            webSocketSessionRegistry.closeMemberSessions(memberId);
        }
        if (groupId != null) {
            jdbcTemplate.update("delete from group_chat_read where group_id = ?", groupId);
            jdbcTemplate.update("delete from group_chat_message where group_id = ?", groupId);
            jdbcTemplate.update("delete from group_member where group_id = ?", groupId);
            jdbcTemplate.update("delete from member_group where id = ?", groupId);
        }
        if (emoticonId != null) {
            jdbcTemplate.update("delete from chat_emoticon where id = ?", emoticonId);
        }
        if (memberId != null) {
            jdbcTemplate.update("delete from member where id = ?", memberId);
        }
        if (stompClient != null) {
            stompClient.stop();
        }
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
    }

    @Test
    @DisplayName("실제 DB에 저장한 메시지를 두 STOMP 세션이 수신한다")
    void sendMessage_RealDatabase_BroadcastsPersistedMessage() throws Exception {
        // given
        Seed seed = createSeed();

        String accessToken = jwtUtil.createAccessToken(seed.memberId(), Role.ROLE_USER);
        StompSession firstSession = connect(accessToken);
        StompSession secondSession = connect(accessToken);
        String chatDestination = CHAT_DESTINATION_FORMAT.formatted(seed.groupId());
        String sendDestination = SEND_DESTINATION_FORMAT.formatted(seed.groupId());
        CountDownLatch firstMessage = new CountDownLatch(1);
        CountDownLatch secondMessage = new CountDownLatch(1);
        AtomicReference<ChatResDTO.Message> firstReceived = new AtomicReference<>();
        AtomicReference<ChatResDTO.Message> secondReceived = new AtomicReference<>();
        subscribe(firstSession, chatDestination, firstMessage, firstReceived);
        subscribe(secondSession, chatDestination, secondMessage, secondReceived);
        assertThat(awaitSubscriptionCount(chatDestination, 2)).isTrue();

        ChatReqDTO.SendMessage request = new ChatReqDTO.SendMessage(
                "db-client-message-1",
                ChatMessageType.TEXT,
                "실제 DB에 저장되는 메시지",
                null
        );

        // when
        firstSession.send(sendDestination, request);

        // then
        assertThat(firstMessage.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(secondMessage.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(firstReceived.get()).isEqualTo(secondReceived.get());
        assertThat(firstReceived.get())
                .extracting(
                        ChatResDTO.Message::clientMessageId,
                        ChatResDTO.Message::groupId,
                        ChatResDTO.Message::type,
                        ChatResDTO.Message::content
                )
                .containsExactly(
                        request.clientMessageId(),
                        groupId,
                        request.type(),
                        request.content()
                );

        ChatMessage storedMessage = chatMessageRepository
                .findByGroupIdAndSenderIdAndClientMessageId(
                        groupId,
                        seed.memberId(),
                        request.clientMessageId())
                .orElseThrow();
        assertThat(storedMessage.getId()).isNotNull();
        assertThat(storedMessage.getMessageType()).isEqualTo(ChatMessageType.TEXT);
        assertThat(storedMessage.getContent()).isEqualTo(request.content());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from group_chat_message where group_id = ? and sender_id = ? and client_message_id = ?",
                Integer.class,
                seed.groupId(),
                seed.memberId(),
                request.clientMessageId()
        )).isEqualTo(1);

        firstSession.disconnect();
        secondSession.disconnect();
    }

    @Test
    @DisplayName("활성 이모티콘 메시지를 실제 DB에 저장하고 두 STOMP 세션에 broadcast한다")
    void sendEmoticon_RealDatabase_BroadcastsPersistedEmoticon() throws Exception {
        // given
        Seed seed = createSeed();
        ChatEmoticon emoticon = chatEmoticonRepository.saveAndFlush(ChatEmoticon.builder()
                .code("BASIC_HELLO_01")
                .assetKey("chat-emoticons/basic-hello.png")
                .contentType("image/png")
                .animated(false)
                .active(true)
                .displayOrder(1)
                .build());
        emoticonId = emoticon.getId();
        when(mediaService.resolveViewUrl(
                "chat-emoticons/basic-hello.png",
                MediaPurpose.CHAT_EMOTICON
        )).thenReturn("https://cdn.example.com/chat-emoticons/basic-hello.png");

        String accessToken = jwtUtil.createAccessToken(seed.memberId(), Role.ROLE_USER);
        StompSession firstSession = connect(accessToken);
        StompSession secondSession = connect(accessToken);
        String chatDestination = CHAT_DESTINATION_FORMAT.formatted(seed.groupId());
        String sendDestination = SEND_DESTINATION_FORMAT.formatted(seed.groupId());
        CountDownLatch firstMessage = new CountDownLatch(1);
        CountDownLatch secondMessage = new CountDownLatch(1);
        AtomicReference<ChatResDTO.Message> firstReceived = new AtomicReference<>();
        AtomicReference<ChatResDTO.Message> secondReceived = new AtomicReference<>();
        subscribe(firstSession, chatDestination, firstMessage, firstReceived);
        subscribe(secondSession, chatDestination, secondMessage, secondReceived);
        assertThat(awaitSubscriptionCount(chatDestination, 2)).isTrue();

        ChatReqDTO.SendMessage request = new ChatReqDTO.SendMessage(
                "db-client-emoticon-1",
                ChatMessageType.EMOTICON,
                null,
                emoticon.getCode()
        );

        // when
        firstSession.send(sendDestination, request);

        // then
        assertThat(firstMessage.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(secondMessage.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(firstReceived.get()).isEqualTo(secondReceived.get());
        assertThat(firstReceived.get().emoticon())
                .extracting(
                        ChatResDTO.Emoticon::id,
                        ChatResDTO.Emoticon::code,
                        ChatResDTO.Emoticon::assetUrl,
                        ChatResDTO.Emoticon::contentType,
                        ChatResDTO.Emoticon::animated
                )
                .containsExactly(
                        emoticon.getId(),
                        emoticon.getCode(),
                        "https://cdn.example.com/chat-emoticons/basic-hello.png",
                        "image/png",
                        false
                );

        ChatMessage storedMessage = chatMessageRepository
                .findByGroupIdAndSenderIdAndClientMessageId(
                        seed.groupId(),
                        seed.memberId(),
                        request.clientMessageId())
                .orElseThrow();
        assertThat(storedMessage.getMessageType()).isEqualTo(ChatMessageType.EMOTICON);
        assertThat(storedMessage.getContent()).isNull();
        assertThat(storedMessage.getEmoticonId()).isEqualTo(emoticon.getId());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from group_chat_message where group_id = ? and sender_id = ? and client_message_id = ?",
                Integer.class,
                seed.groupId(),
                seed.memberId(),
                request.clientMessageId()
        )).isEqualTo(1);

        firstSession.disconnect();
        secondSession.disconnect();
    }

    private Seed createSeed() {
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "");
        Member member = memberRepository.save(Member.builder()
                .email("chat-websocket-" + suffix + "@example.com")
                .nickname("DB 채팅 테스트")
                .socialProvider(SocialProvider.KAKAO)
                .socialId("chat-websocket-" + suffix)
                .role(Role.ROLE_USER)
                .build());
        memberId = member.getId();

        Group group = groupRepository.save(Group.builder()
                .name("DB채팅" + suffix.substring(0, 8))
                .inviteCode(suffix.substring(0, 7).toUpperCase())
                .build());
        groupId = group.getId();
        groupMemberRepository.saveAndFlush(GroupMember.builder()
                .member(member)
                .group(group)
                .role(GroupMemberRole.MEMBER)
                .build());
        return new Seed(groupId, memberId);
    }

    private StompSession connect(String accessToken) throws Exception {
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setBearerAuth(accessToken);
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
            String destination,
            CountDownLatch messageReceived,
            AtomicReference<ChatResDTO.Message> receivedMessage
    ) {
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
                return ChatResDTO.Message.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedMessage.set((ChatResDTO.Message) payload);
                messageReceived.countDown();
            }
        });
    }

    private boolean awaitSubscriptionCount(String destination, int expectedCount) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            int subscriptionCount = simpUserRegistry.findSubscriptions(
                    subscription -> destination.equals(subscription.getDestination())
            ).size();
            if (subscriptionCount == expectedCount) {
                return true;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        return false;
    }

    private record Seed(Long groupId, Long memberId) {
    }
}
