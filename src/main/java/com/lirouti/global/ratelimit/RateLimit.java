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
 * @RateLimit("media-presign")
 * @PostMapping("/presigned-url")
 * public ApiResponse<...> issuePresignedUrl(...) { ... }
 * }</pre>
 *
 * 설정에 없는 이름을 적으면 <b>제한이 걸리지 않고 ERROR 로그만 남는다.</b> 오타 때문에 정상
 * 요청이 막히는 것보다, 보호가 빠진 것을 로그로 알리는 편이 낫다고 봤다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /** {@code rate-limit.policies}의 키. */
    String value();
}
