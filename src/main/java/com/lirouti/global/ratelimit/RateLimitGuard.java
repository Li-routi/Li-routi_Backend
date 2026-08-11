package com.lirouti.global.ratelimit;

import com.lirouti.global.auth.CustomUserDetails;
import com.lirouti.global.properties.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 정책 하나를 한 번 소진하고, 한도를 넘겼으면 막는다.
 *
 * <p><b>이 판정을 인터셉터에서 떼어낸 이유가 있다.</b> 인터셉터는 핸들러가 정해진 뒤·요청 본문을
 * 읽기 전에 돌아 {@code @RateLimit}의 <b>정적 문자열</b>밖에 볼 수 없다. 그런데 presigned URL
 * 발급은 요청 본문의 {@code purpose}에 따라 다른 정책을 써야 한다(인증 사진과 프로필 사진이 같은
 * 예산을 나눠 쓰면, 프로필을 몇 번 바꿨다고 인증이 막힌다). 인터셉터에서 본문을 읽으면 그 스트림을
 * 컨트롤러가 다시 못 읽으므로, 본문이 이미 파싱된 <b>서비스에서 부르는 길</b>을 열어 둔다.
 *
 * <p>그래서 소진 로직이 두 곳에서 필요해졌고, 복붙 대신 여기로 모았다.
 * {@link RateLimitInterceptor}도 이 클래스를 부른다.
 *
 * <p><b>서비스에서 부르면 검증을 통과한 요청만 소진한다.</b> 인터셉터는 {@code @Valid}보다 먼저
 * 돌아 형식·용량이 틀린 요청도 한 건을 깎았다. 호출 위치를 옮기면서 그 낭비가 함께 사라진다.
 *
 * <p><b>Redis가 죽으면 프로세스 안 카운터로 계속 센다.</b> 그냥 통과시키면 상한이 통째로
 * 사라지고, 막아버리면 막으려던 남용보다 큰 장애가 된다. 폴백까지 실패하면 그때는 통과시킨다 —
 * 레이트 리밋은 인가가 아니라 보호 장치다. 대신 degrade 된 사실을 로그로 남긴다.
 */
@Slf4j
@Component
public class RateLimitGuard {
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

    public RateLimitGuard(RateLimiter rateLimiter, RateLimitProperties properties, Clock clock) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        // 폴백은 Redis 가 죽었을 때만 쓰이므로 평소에는 비어 있고, 빈으로 올려 다른 곳이
        // 쓰게 할 이유도 없다. 이 클래스와 수명을 맞춘다.
        this.fallbackLimiter = new InMemoryRateLimiter(clock);
    }

    /**
     * 정책 하나를 소진한다. 한도를 넘겼으면 {@link RateLimitExceededException}을 던진다.
     *
     * <p>정책 이름이 {@code null}이면 아무것도 하지 않는다. "이 용도는 제한하지 않는다"를
     * 호출부가 분기 없이 표현할 수 있게 한 것이다.
     *
     * @param policyName {@code rate-limit.policies}의 키. {@code null}이면 통과
     */
    public void enforce(String policyName) {
        if (policyName == null || !properties.isEnabled()) {
            return;
        }
        RateLimitProperties.Policy policy = properties.getPolicies().get(policyName);
        if (policy == null) {
            log.error("레이트 리밋 정책 '{}'이 설정에 없어 제한을 걸지 못했습니다."
                    + " rate-limit.policies에 추가하세요.", policyName);
            return;
        }

        String identity = resolveIdentity();
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
                return;
            }
        }

        if (!result.allowed()) {
            log.warn("레이트 리밋 초과. policy={}, identity={}, count={}/{}, retryAfter={}s",
                    policyName, identity, result.count(), policy.getLimit(),
                    result.retryAfterSeconds());
            throw new RateLimitExceededException(result.retryAfterSeconds());
        }
    }

    /**
     * 그 이름의 정책이 설정에 있는지 확인한다.
     *
     * <p><b>부팅 시 검증용이다.</b> {@link #enforce} 는 없는 정책을 만나면 통과시키므로(fail-open),
     * 정책 이름을 문자열로 들고 있는 쪽이 오타를 내면 <b>제한이 조용히 사라진다.</b> 로그는
     * 남지만 아무도 안 본다. 그래서 이름을 하드코딩하는 쪽이 부팅 때 스스로 확인할 수 있게 연다.
     */
    public boolean hasPolicy(String policyName) {
        return properties.getPolicies().containsKey(policyName);
    }

    /**
     * Redis 경로가 죽었다는 사실을 남긴다. 간격 안에 다시 불리면 조용히 지나간다.
     *
     * <p>시각은 {@link System#currentTimeMillis()}를 쓴다. 로그 빈도 조절이 목적이라
     * 벽시계가 틀어져도 문제가 없다. 주입한 {@code Clock}은 폴백 카운터의 창 계산용이라
     * 테스트가 시간을 앞으로 감는데, 그 시계를 로그 간격에 같이 쓰면 두 관심사가 얽힌다.
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
     * <p>지금 제한을 거는 곳은 모두 로그인이 필요해 회원 id로 세므로 드러나지 않는다.
     * <b>익명 경로에 붙이는 순간 문제가 된다</b> — 그때 {@code X-Forwarded-For}를 봐야 하는데,
     * 그 헤더는 클라이언트가 위조할 수 있으므로 <b>Caddy가 덮어쓴 값만</b> 믿어야 한다.
     *
     * <p>요청을 인자로 받지 않고 {@link RequestContextHolder}에서 꺼내는 것은 호출부 때문이다.
     * 인터셉터는 요청을 손에 들고 있지만 서비스는 그렇지 않다. 요청을 인자로 만들면 서비스가
     * 웹 계층 타입을 알아야 하므로, 꺼내는 일을 이 안에 둔다. 요청 밖에서 불리면(스케줄러 등)
     * 신원을 알 수 없어 {@code anonymous}로 합쳐지는데, 그런 호출부는 아직 없다.
     */
    private String resolveIdentity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return "member:" + userDetails.getMemberId();
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            return "ip:" + request.getRemoteAddr();
        }
        return "anonymous";
    }
}
