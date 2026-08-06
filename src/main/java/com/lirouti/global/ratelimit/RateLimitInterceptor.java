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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link RateLimit}이 붙은 핸들러에 사용자당 요청 빈도 제한을 적용한다(#23).
 *
 * <b>Redis가 죽으면 프로세스 안 카운터로 계속 센다.</b> 예전에는 그냥 통과시켰는데(fail-open),
 * presigned 발급은 한 건이 10MB 업로드를 허용해서 그동안 상한이 통째로 사라졌다.
 * 그렇다고 막아버리면 막으려던 남용보다 큰 장애가 되므로, 그 사이를 폴백으로 채운다
 * ({@link InMemoryRateLimiter}).
 *
 * <b>폴백까지 실패하면 그때는 통과시킨다.</b> 레이트 리밋은 인가가 아니라 보호 장치라는
 * 판단은 그대로다. 대신 보호가 degrade 된 사실을 로그로 남긴다.
 *
 * <b>{@code @Component}가 아니다.</b> {@code @WebMvcTest} 슬라이스는 {@code HandlerInterceptor}
 * 구현체를 빈으로 올리려 하는데, 그 슬라이스에는 {@code RateLimiter}가 없어 <b>레이트 리밋과
 * 무관한 컨트롤러 테스트가 전부 컨텍스트 로딩에서 깨진다.</b> 그래서 빈으로 두지 않고
 * {@code WebConfig}가 직접 생성한다.
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {
    /**
     * degrade 로그를 이 간격으로만 남긴다.
     *
     * <p>Redis 장애는 몇 초로 끝나지 않는다. 요청마다 스택 트레이스를 찍으면 <b>정작 원인을
     * 찾을 때 로그가 그 예외로 가득 차</b> 다른 단서가 묻힌다. 그렇다고 한 번만 찍으면 장애가
     * 아직 계속되는지 알 수 없어, 간격을 두고 반복해 남긴다.
     */
    private static final long DEGRADED_LOG_INTERVAL_MILLIS = 60_000L;

    private final RateLimiter rateLimiter;
    private final InMemoryRateLimiter fallbackLimiter;
    private final RateLimitProperties properties;

    private final AtomicBoolean degradedLogged = new AtomicBoolean(false);
    private final AtomicLong lastDegradedLogAt = new AtomicLong();

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
            logDegraded(key, e);
            try {
                result = fallbackLimiter.consume(key, policy.getLimit(), policy.getWindow());
            } catch (RuntimeException fallbackFailure) {
                log.error("폴백 레이트 리밋까지 실패해 요청을 통과시킵니다. key={}", key, fallbackFailure);
                return true;
            }
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
     * Redis 경로가 죽었다는 사실을 남긴다. 간격 안에 다시 불리면 조용히 지나간다.
     *
     * <p>시각은 {@link System#currentTimeMillis()}를 쓴다. 로그 빈도 조절이 목적이라
     * 벽시계가 틀어져도 문제가 없고, 이 자리에 {@code Clock}을 주입하면 인터셉터가
     * 로그 때문에 의존을 하나 더 갖게 된다.
     */
    private void logDegraded(String key, RuntimeException cause) {
        if (!shouldLogDegraded(System.currentTimeMillis())) {
            return;
        }
        log.error("레이트 리밋 저장소를 쓸 수 없어 프로세스 안 카운터로 대신 셉니다"
                + "(인스턴스가 여럿이면 인스턴스별 제한으로 degrade 됩니다). key={}", key, cause);
    }

    /**
     * 지금 남길 차례인지 판단하고, 남길 거면 시각을 갱신한다.
     *
     * <p><b>"아직 한 번도 안 남겼다"를 시각으로 표현하지 않는다.</b> 이전에는 {@code Long.MIN_VALUE}
     * 로 두었는데, {@code now - Long.MIN_VALUE} 가 오버플로해 음수가 되는 바람에
     * <b>정작 첫 장애 로그가 안 찍혔다.</b> 남기려던 것이 안 남는 정반대 결과라, 첫 회 여부를
     * 플래그로 따로 들고 시각 비교는 두 번째부터 한다.
     *
     * <p>동시 요청 중 하나만 남기도록 CAS 로 자리를 잡는다. 진 쪽은 조용히 지나간다.
     */
    boolean shouldLogDegraded(long now) {
        if (degradedLogged.compareAndSet(false, true)) {
            lastDegradedLogAt.set(now);
            return true;
        }
        long last = lastDegradedLogAt.get();
        return now - last >= DEGRADED_LOG_INTERVAL_MILLIS
                && lastDegradedLogAt.compareAndSet(last, now);
    }

    /**
     * 로그인 사용자는 회원 id로, 그렇지 않으면 원격 주소로 센다.
     *
     * <b>운영에는 앞단에 Caddy가 있어 {@code getRemoteAddr()}가 클라이언트가 아니라 프록시 주소다.</b>
     * 즉 익명 요청은 전부 한 identity로 합쳐져, 그 경로의 제한이 사실상 전역 제한이 된다.
     *
     * <p>지금 {@code @RateLimit}이 붙은 곳은 로그인이 필요해 회원 id로 세므로 드러나지 않는다.
     * <b>익명 경로에 붙이는 순간 문제가 된다</b> — 그때 {@code X-Forwarded-For}를 봐야 하는데,
     * 그 헤더는 클라이언트가 위조할 수 있으므로 <b>Caddy가 덮어쓴 값만</b> 믿어야 한다.
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
