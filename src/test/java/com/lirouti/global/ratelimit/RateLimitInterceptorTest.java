package com.lirouti.global.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * 판정 자체는 {@link RateLimitGuard} 가 하고 여기서는 검증하지 않는다
 * ({@code RateLimitGuardTest}). 이 테스트가 보는 것은 <b>애노테이션을 읽어 정책 이름을
 * 넘기는 부분</b>뿐이다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitInterceptor 테스트")
class RateLimitInterceptorTest {
    private static final String POLICY = "media-presign-verification";

    @Mock
    private RateLimitGuard guard;

    private RateLimitInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

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
        interceptor = new RateLimitInterceptor(guard);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Method method = AnnotatedController.class.getMethod(methodName);
        return new HandlerMethod(new AnnotatedController(), method);
    }

    @Test
    @DisplayName("애노테이션이 없는 핸들러는 판정을 부르지 않는다")
    void preHandle_NoAnnotation_SkipsEntirely() throws Exception {
        assertThat(interceptor.preHandle(request, response, handler("unlimited"))).isTrue();
        verifyNoInteractions(guard);
    }

    @Test
    @DisplayName("핸들러 메서드가 아니면(정적 리소스 등) 그대로 통과시킨다")
    void preHandle_NotHandlerMethod_Passes() {
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verifyNoInteractions(guard);
    }

    @Test
    @DisplayName("애노테이션의 정책 이름을 그대로 넘긴다")
    void preHandle_Annotated_DelegatesPolicyName() throws Exception {
        assertThat(interceptor.preHandle(request, response, handler("limited"))).isTrue();
        verify(guard).enforce(POLICY);
    }

    @Test
    @DisplayName("한도를 넘겨 던질 때 응답에 직접 쓰지 않는다 — 본문은 예외 처리기가 만든다")
    void preHandle_OverLimit_DoesNotWriteResponseDirectly() throws Exception {
        doThrow(new RateLimitExceededException(10)).when(guard).enforce(POLICY);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("limited")))
                .isInstanceOf(RateLimitExceededException.class);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
