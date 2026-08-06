package com.lirouti.global.ratelimit;

import com.lirouti.domain.member.enums.Role;
import com.lirouti.global.auth.CustomUserDetails;
import com.lirouti.global.properties.RateLimitProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitInterceptor 테스트")
class RateLimitInterceptorTest {
    private static final String POLICY = "media-presign";
    private static final int LIMIT = 20;

    @Mock
    private RateLimiter rateLimiter;

    private RateLimitProperties properties;
    private RateLimitInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private InMemoryRateLimiter fallbackLimiter;

    /** {@code @RateLimit}이 붙은 핸들러를 흉내내기 위한 대상. */
    static class AnnotatedController {
        @RateLimit(POLICY)
        public void limited() {
        }

        public void unlimited() {
        }
    }

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        RateLimitProperties.Policy policy = new RateLimitProperties.Policy();
        policy.setLimit(LIMIT);
        policy.setWindow(Duration.ofHours(1));
        properties.getPolicies().put(POLICY, policy);

        fallbackLimiter = new InMemoryRateLimiter(Clock.systemUTC());
        interceptor = new RateLimitInterceptor(rateLimiter, fallbackLimiter, properties);
        request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.7");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Method method = AnnotatedController.class.getMethod(methodName);
        return new HandlerMethod(new AnnotatedController(), method);
    }

    private void authenticateAs(Long memberId) {
        CustomUserDetails principal = new CustomUserDetails(memberId, Role.ROLE_USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void mockConsume(boolean allowed, long retryAfter) {
        when(rateLimiter.consume(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(new RateLimiter.Result(allowed, 1, retryAfter));
    }

    @Test
    @DisplayName("애노테이션이 없는 핸들러는 Redis를 아예 건드리지 않는다")
    void preHandle_NoAnnotation_SkipsEntirely() throws Exception {
        assertThat(interceptor.preHandle(request, response, handler("unlimited"))).isTrue();
        verifyNoInteractions(rateLimiter);
    }

    @Test
    @DisplayName("핸들러 메서드가 아니면(정적 리소스 등) 그대로 통과시킨다")
    void preHandle_NotHandlerMethod_Passes() {
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verifyNoInteractions(rateLimiter);
    }

    @Test
    @DisplayName("전체 스위치를 끄면 검사하지 않는다 — 탈출구가 실제로 동작하는지")
    void preHandle_Disabled_SkipsCheck() throws Exception {
        properties.setEnabled(false);

        assertThat(interceptor.preHandle(request, response, handler("limited"))).isTrue();
        verifyNoInteractions(rateLimiter);
    }

    @Test
    @DisplayName("설정에 없는 정책이면 막지 않고 통과시킨다 — 오타로 정상 요청이 막히지 않게")
    void preHandle_UnknownPolicy_FailsOpen() throws Exception {
        properties.getPolicies().clear();

        assertThat(interceptor.preHandle(request, response, handler("limited"))).isTrue();
        verifyNoInteractions(rateLimiter);
    }

    @Test
    @DisplayName("한도 안이면 통과한다")
    void preHandle_UnderLimit_Passes() throws Exception {
        mockConsume(true, 0);

        assertThat(interceptor.preHandle(request, response, handler("limited"))).isTrue();
    }

    @Test
    @DisplayName("한도를 넘으면 429로 던지고 Retry-After를 실어 보낸다")
    void preHandle_OverLimit_Throws429() throws Exception {
        mockConsume(false, 42);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("limited")))
                .isInstanceOf(RateLimitExceededException.class)
                .hasFieldOrPropertyWithValue("retryAfterSeconds", 42L);
    }

    @Test
    @DisplayName("Redis가 죽어도 한도 안이면 통과한다 — 보호 장치가 장애를 만들지 않게")
    void preHandle_RedisFailure_StillPassesUnderLimit() throws Exception {
        when(rateLimiter.consume(anyString(), anyInt(), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis down"));

        assertThat(interceptor.preHandle(request, response, handler("limited"))).isTrue();
    }

    @Test
    @DisplayName("Redis가 죽어도 한도를 넘으면 막는다 — 예전에는 여기가 무제한이었다")
    void preHandle_RedisFailure_FallbackStillBlocks() throws Exception {
        when(rateLimiter.consume(anyString(), anyInt(), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis down"));

        for (int i = 0; i < LIMIT; i++) {
            assertThat(interceptor.preHandle(request, response, handler("limited")))
                    .as("%d번째는 폴백 한도 안이다", i + 1)
                    .isTrue();
        }

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("limited")))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("로그인 사용자는 회원 id로 센다 — 같은 IP의 다른 사용자가 서로 영향을 주지 않게")
    void preHandle_Authenticated_KeyedByMemberId() throws Exception {
        authenticateAs(42L);
        mockConsume(true, 0);

        interceptor.preHandle(request, response, handler("limited"));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(rateLimiter).consume(key.capture(), eq(LIMIT), any(Duration.class));
        assertThat(key.getValue()).isEqualTo("rate-limit:media-presign:member:42");
    }

    @Test
    @DisplayName("비로그인 요청은 원격 주소로 센다")
    void preHandle_Anonymous_KeyedByIp() throws Exception {
        mockConsume(true, 0);

        interceptor.preHandle(request, response, handler("limited"));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(rateLimiter).consume(key.capture(), anyInt(), any(Duration.class));
        assertThat(key.getValue()).isEqualTo("rate-limit:media-presign:ip:10.0.0.7");
    }

    @Test
    @DisplayName("한도를 넘겨 던질 때 응답에 직접 쓰지 않는다 — 본문은 예외 처리기가 만든다")
    void preHandle_OverLimit_DoesNotWriteResponseDirectly() throws Exception {
        mockConsume(false, 10);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("limited")))
                .isInstanceOf(RateLimitExceededException.class);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
