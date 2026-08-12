package com.lirouti.global.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@link RateLimit}이 붙은 핸들러에 사용자당 요청 빈도 제한을 적용한다(#23).
 *
 * <p><b>판정 자체는 하지 않고 {@link RateLimitGuard}에 넘긴다.</b> 같은 판정을 서비스에서도
 * 불러야 해서(요청 본문의 값에 따라 정책이 갈리는 경우) 로직을 그쪽으로 모았다. 여기 남은 일은
 * "이 핸들러에 애노테이션이 붙었는가"를 읽어 정책 이름을 건네는 것뿐이다.
 *
 * <p><b>{@code @Component}가 아니다.</b> {@code @WebMvcTest} 슬라이스는 {@code HandlerInterceptor}
 * 구현체를 빈으로 올리려 하는데, 그 슬라이스에는 {@code RateLimitGuard}가 없어 <b>레이트 리밋과
 * 무관한 컨트롤러 테스트가 전부 컨텍스트 로딩에서 깨진다.</b> 그래서 빈으로 두지 않고
 * {@code WebConfig}가 직접 생성한다.
 */
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {
    private final RateLimitGuard guard;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimit annotation = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (annotation == null) {
            return true;
        }
        guard.enforce(annotation.value());
        return true;
    }
}
