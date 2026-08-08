package com.lirouti.domain.member.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.concurrent.TimeUnit;
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
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.exception.code.success.MemberSuccessCode;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.global.util.JwtUtil;
import com.lirouti.global.util.RedisUtil;
import com.lirouti.global.websocket.WebSocketSessionRegistry;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("회원 탈퇴 WebSocket 통합 테스트")
class MemberWithdrawalWebSocketIntegrationTest {
    private static final String WITHDRAWAL_CONFIRMATION = "리루티를 탈퇴합니다";

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private WebSocketSessionRegistry webSocketSessionRegistry;

    @MockitoBean
    private RedisUtil redisUtil;

    private final Set<String> blacklistedTokens = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private WebSocketStompClient stompClient;
    private ThreadPoolTaskScheduler taskScheduler;
    private Long memberId;

    @BeforeEach
    void setUp() {
        blacklistedTokens.clear();
        when(redisUtil.isBlackList(anyString()))
                .thenAnswer(invocation -> blacklistedTokens.contains(invocation.getArgument(0)));
        doAnswer(invocation -> {
            blacklistedTokens.add(invocation.getArgument(0));
            return null;
        }).when(redisUtil).setBlackList(anyString(), anyLong());

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
            memberRepository.deleteById(memberId);
            memberRepository.flush();
        }
        if (stompClient != null) {
            stompClient.stop();
        }
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
    }

    @Test
    @DisplayName("회원 탈퇴 커밋 후 이벤트가 모든 실제 WebSocket 세션을 종료한다")
    void withdraw_AfterCommit_ClosesAllWebSocketSessions() throws Exception {
        // given
        Member member = memberRepository.saveAndFlush(Member.builder()
                .email("withdrawal-websocket-" + System.nanoTime() + "@example.com")
                .nickname("탈퇴 테스트")
                .socialProvider(SocialProvider.KAKAO)
                .socialId("withdrawal-websocket-" + System.nanoTime())
                .role(Role.ROLE_USER)
                .build());
        memberId = member.getId();
        String accessToken = jwtUtil.createAccessToken(memberId, Role.ROLE_USER);

        StompSession firstSession = connect(accessToken);
        StompSession secondSession = connect(accessToken);
        assertThat(firstSession.isConnected()).isTrue();
        assertThat(secondSession.isConnected()).isTrue();

        // when
        mockMvc.perform(delete("/api/members/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("{\"confirmation\":\"" + WITHDRAWAL_CONFIRMATION + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value(MemberSuccessCode.MEMBER_WITHDRAWAL_SUCCESS.getCode()));

        // then
        assertThat(awaitDisconnected(firstSession, secondSession)).isTrue();
        assertThat(memberRepository.findById(memberId))
                .get()
                .extracting(Member::isActiveMember)
                .isEqualTo(false);
        assertThat(blacklistedTokens).contains(accessToken);
    }

    private StompSession connect(String accessToken) throws Exception {
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setBearerAuth(accessToken);
        StompHeaders connectHeaders = new StompHeaders();

        return stompClient.connectAsync(
                        "ws://localhost:" + port + "/ws",
                        handshakeHeaders,
                        connectHeaders,
                        new StompSessionHandlerAdapter() {
                        }
                )
                .get(5, TimeUnit.SECONDS);
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
