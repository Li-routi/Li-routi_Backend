package com.lirouti.global.ratelimit;

import com.lirouti.global.auth.CustomUserDetails;
import com.lirouti.global.properties.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@link RateLimit}이 붙은 핸들러에 사용자당 요청 빈도 제한을 적용한다(#23).
 *
 * <b>실패 시 통과시킨다(fail-open).</b> Redis가 죽었다고 업로드를 통째로 막으면, 막으려던
 * 남용보다 더 큰 장애가 된다. 레이트 리밋은 인가가 아니라 보호 장치라는 판단이다.
 * 대신 실패는 ERROR로 남겨 보호가 꺼진 사실이 묻히지 않게 한다.
 *
 * <b>{@code @Component}가 아니다.</b> {@code @WebMvcTest} 슬라이스는 {@code HandlerInterceptor}
 * 구현체를 빈으로 올리려 하는데, 그 슬라이스에는 {@code RateLimiter}가 없어 <b>레이트 리밋과
 * 무관한 컨트롤러 테스트가 전부 컨텍스트 로딩에서 깨진다.</b> 그래서 빈으로 두지 않고
 * {@code WebConfig}가 직접 생성한다.
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {
    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimit annotation = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (annotation == null || !properties.isEnabled()) {
            return true;
        }

        String policyName = annotation.value();
        RateLimitProperties.Policy policy = properties.getPolicies().get(policyName);
        if (policy == null) {
            log.error("레이트 리밋 정책 '{}'이 설정에 없어 제한을 걸지 못했습니다."
                    + " rate-limit.policies에 추가하세요. handler={}",
                    policyName, handlerMethod.getMethod().getName());
            return true;
        }

        String identity = resolveIdentity(request);
        String key = "rate-limit:%s:%s".formatted(policyName, identity);

        RateLimiter.Result result;
        try {
            result = rateLimiter.consume(key, policy.getLimit(), policy.getWindow());
        } catch (RuntimeException e) {
            log.error("레이트 리밋 검사에 실패해 요청을 통과시킵니다(보호가 꺼진 상태입니다). key={}", key, e);
            return true;
        }

        if (!result.allowed()) {
            log.warn("레이트 리밋 초과. policy={}, identity={}, count={}/{}, retryAfter={}s",
                    policyName, identity, result.count(), policy.getLimit(),
                    result.retryAfterSeconds());
            throw new RateLimitExceededException(result.retryAfterSeconds());
        }
        return true;
    }

    /**
     * 로그인 사용자는 회원 id로, 그렇지 않으면 원격 주소로 센다.
     *
     * 이 앱은 리버스 프록시 없이 8080을 직접 노출하므로 {@code getRemoteAddr()}가 실제 클라이언트
     * 주소다. <b>앞단에 프록시·로드밸런서를 두게 되면</b> 모든 요청이 프록시 IP 하나로 합쳐져
     * 익명 경로의 제한이 사실상 전역 제한이 된다 — 그때 {@code X-Forwarded-For} 처리가 필요하다.
     */
    private String resolveIdentity(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return "member:" + userDetails.getMemberId();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
