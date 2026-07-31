package com.lirouti.global.config;

import com.lirouti.global.properties.RateLimitProperties;
import com.lirouti.global.ratelimit.RateLimitInterceptor;
import com.lirouti.global.ratelimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 인터셉터 등록.
 *
 * 레이트 리밋(#23)을 필터가 아니라 인터셉터로 두는 이유는 <b>어느 핸들러가 처리할 요청인지
 * 알아야</b> 하기 때문이다. 필터는 핸들러 매핑 전에 돌아 {@code @RateLimit}을 읽을 수 없어
 * 경로를 따로 나열해 관리해야 한다. 인터셉터는 애노테이션이 붙은 자리에서만 동작한다.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    /**
     * 필수 의존이 아니라 {@link ObjectProvider}인 것은 {@code @WebMvcTest} 때문이다.
     *
     * MVC 슬라이스는 {@code WebMvcConfigurer}인 이 클래스는 올리면서 일반 {@code @Component}인
     * {@code RateLimiter}·{@code RateLimitProperties}는 제외한다. 필수로 두면 레이트 리밋과
     * 무관한 컨트롤러 슬라이스 테스트가 전부 컨텍스트 로딩에서 깨진다. 슬라이스마다 목을 등록해
     * 해결할 수도 있지만, 그러면 앞으로 만드는 모든 MVC 테스트가 이 사정을 알아야 한다.
     *
     * 실제 애플리케이션 컨텍스트에는 둘 다 항상 있으므로 운영 동작은 달라지지 않는다.
     */
    private final ObjectProvider<RateLimiter> rateLimiter;
    private final ObjectProvider<RateLimitProperties> rateLimitProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        RateLimiter limiter = rateLimiter.getIfAvailable();
        RateLimitProperties properties = rateLimitProperties.getIfAvailable();
        if (limiter == null || properties == null) {
            return;
        }
        // 경로를 제한하지 않는다. @RateLimit이 붙은 핸들러에서만 실제로 동작하므로
        // 여기서 경로를 나열하면 애노테이션과 두 곳에서 관리하게 된다.
        registry.addInterceptor(new RateLimitInterceptor(limiter, properties));
    }
}
