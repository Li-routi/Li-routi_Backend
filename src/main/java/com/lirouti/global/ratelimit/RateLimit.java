package com.lirouti.global.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 핸들러에 사용자당 요청 빈도 제한을 건다(#23).
 *
 * 값은 {@code rate-limit.policies}의 키다. 한도(횟수·창 길이)는 설정에서 관리하므로
 * 여기에는 정책 이름만 적는다.
 *
 * <pre>{@code
 * @RateLimit("정책이름")
 * @PostMapping("/some-endpoint")
 * public ApiResponse<...> handle(...) { ... }
 * }</pre>
 *
 * 설정에 없는 이름을 적으면 <b>제한이 걸리지 않고 ERROR 로그만 남는다.</b> 오타 때문에 정상
 * 요청이 막히는 것보다, 보호가 빠진 것을 로그로 알리는 편이 낫다고 봤다.
 *
 * <p><b>지금 이 애노테이션을 쓰는 곳은 없다.</b> 유일한 사용처였던 presigned URL 발급이
 * {@link RateLimitGuard} 를 서비스에서 직접 부르는 방식으로 옮겨 갔다 — 정책이 요청 본문의
 * 값에 따라 갈리는데, 인터셉터는 본문을 읽기 전에 돌아 정적 문자열밖에 볼 수 없기 때문이다.
 * 요청 값과 무관하게 한 정책으로 제한할 엔드포인트에는 이 애노테이션이 여전히 가장 간단하다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /** {@code rate-limit.policies}의 키. */
    String value();
}
