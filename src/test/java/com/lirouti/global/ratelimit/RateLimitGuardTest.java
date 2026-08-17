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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitGuard 테스트")
class RateLimitGuardTest {
    private static final String POLICY = "media-presign-verification";
    private static final int LIMIT = 20;

    @Mock
    private RateLimiter rateLimiter;

    private RateLimitProperties properties;
    private RateLimitGuard guard;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        RateLimitProperties.Policy policy = new RateLimitProperties.Policy();
        policy.setLimit(LIMIT);
        policy.setWindow(Duration.ofMinutes(10));
        properties.getPolicies().put(POLICY, policy);

        guard = new RateLimitGuard(rateLimiter, properties, Clock.systemUTC());

        // 익명 요청의 신원은 요청의 원격 주소에서 온다. guard 는 인자로 받지 않고
        // RequestContextHolder 에서 꺼내므로 여기에 심어 둔다.
        request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
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
    @DisplayName("정책 이름이 null이면 아무것도 세지 않는다 — 제한 대상이 아닌 용도를 분기 없이 표현한다")
    void enforce_NullPolicy_SkipsEntirely() {
        assertThatCode(() -> guard.enforce(null)).doesNotThrowAnyException();
        verifyNoInteractions(rateLimiter);
    }

    @Test
    @DisplayName("전체 스위치를 끄면 검사하지 않는다 — 탈출구가 실제로 동작하는지")
    void enforce_Disabled_SkipsCheck() {
        properties.setEnabled(false);

        guard.enforce(POLICY);

        verifyNoInteractions(rateLimiter);
    }

    @Test
    @DisplayName("설정에 없는 정책이면 막지 않고 통과시킨다 — 오타로 정상 요청이 막히지 않게")
    void enforce_UnknownPolicy_FailsOpen() {
        properties.getPolicies().clear();

        assertThatCode(() -> guard.enforce(POLICY)).doesNotThrowAnyException();
        verifyNoInteractions(rateLimiter);
    }

    @Test
    @DisplayName("한도 안이면 통과한다")
    void enforce_UnderLimit_Passes() {
        mockConsume(true, 0);

        assertThatCode(() -> guard.enforce(POLICY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("한도를 넘으면 429로 던지고 Retry-After를 실어 보낸다")
    void enforce_OverLimit_Throws429() {
        mockConsume(false, 42);

        assertThatThrownBy(() -> guard.enforce(POLICY))
                .isInstanceOf(RateLimitExceededException.class)
                .hasFieldOrPropertyWithValue("retryAfterSeconds", 42L);
    }

    @Test
    @DisplayName("Redis가 죽어도 한도 안이면 통과한다 — 보호 장치가 장애를 만들지 않게")
    void enforce_RedisFailure_StillPassesUnderLimit() {
        when(rateLimiter.consume(anyString(), anyInt(), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis down"));

        assertThatCode(() -> guard.enforce(POLICY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Redis가 죽어도 한도를 넘으면 막는다 — 예전에는 여기가 무제한이었다")
    void enforce_RedisFailure_FallbackStillBlocks() {
        when(rateLimiter.consume(anyString(), anyInt(), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis down"));

        for (int i = 0; i < LIMIT; i++) {
            int attempt = i + 1;
            assertThatCode(() -> guard.enforce(POLICY))
                    .as("%d번째는 폴백 한도 안이다", attempt)
                    .doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> guard.enforce(POLICY))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("정책이 다르면 폴백 카운터도 따로 센다 — 인증 예산과 프로필 예산이 섞이지 않게")
    void enforce_DifferentPolicies_CountSeparately() {
        RateLimitProperties.Policy profile = new RateLimitProperties.Policy();
        profile.setLimit(1);
        profile.setWindow(Duration.ofMinutes(10));
        properties.getPolicies().put("media-presign-profile", profile);
        when(rateLimiter.consume(anyString(), anyInt(), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis down"));

        guard.enforce("media-presign-profile");

        // 프로필 예산을 다 썼어도 인증은 그대로 남아 있어야 한다.
        assertThatThrownBy(() -> guard.enforce("media-presign-profile"))
                .isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> guard.enforce(POLICY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("첫 degrade 로그는 반드시 남는다 — 예전에는 오버플로로 이것이 빠졌다")
    void shouldLogDegraded_FirstCall_Logs() {
        long now = System.currentTimeMillis();

        assertThat(guard.shouldLogDegraded(now))
                .as("한 번도 안 남긴 상태의 첫 호출")
                .isTrue();
    }

    @Test
    @DisplayName("간격 안에는 다시 남기지 않고, 간격이 지나면 다시 남긴다")
    void shouldLogDegraded_ThrottlesWithinInterval() {
        long now = System.currentTimeMillis();
        guard.shouldLogDegraded(now);

        assertThat(guard.shouldLogDegraded(now + 59_000))
                .as("장애가 길어져도 요청마다 찍으면 로그가 넘친다")
                .isFalse();
        assertThat(guard.shouldLogDegraded(now + 60_000))
                .as("한 번만 찍으면 장애가 계속되는지 알 수 없다")
                .isTrue();
    }

    @Test
    @DisplayName("로그인 사용자는 회원 id로 센다 — 같은 IP의 다른 사용자가 서로 영향을 주지 않게")
    void enforce_Authenticated_KeyedByMemberId() {
        authenticateAs(42L);
        mockConsume(true, 0);

        guard.enforce(POLICY);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(rateLimiter).consume(key.capture(), eq(LIMIT), any(Duration.class));
        assertThat(key.getValue()).isEqualTo("rate-limit:media-presign-verification:member:42");
    }

    @Test
    @DisplayName("비로그인 요청은 원격 주소로 센다")
    void enforce_Anonymous_KeyedByIp() {
        mockConsume(true, 0);

        guard.enforce(POLICY);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(rateLimiter).consume(key.capture(), anyInt(), any(Duration.class));
        assertThat(key.getValue()).isEqualTo("rate-limit:media-presign-verification:ip:10.0.0.7");
    }

    @Test
    @DisplayName("요청 밖에서 불려도 터지지 않는다 — 신원을 못 찾으면 한 키로 합쳐 센다")
    void enforce_OutsideRequest_FallsBackToAnonymous() {
        RequestContextHolder.resetRequestAttributes();
        mockConsume(true, 0);

        guard.enforce(POLICY);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(rateLimiter).consume(key.capture(), anyInt(), any(Duration.class));
        assertThat(key.getValue()).isEqualTo("rate-limit:media-presign-verification:anonymous");
    }
}
