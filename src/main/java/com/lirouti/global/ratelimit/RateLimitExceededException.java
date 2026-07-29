package com.lirouti.global.ratelimit;

import com.lirouti.global.apiPayload.code.GeneralErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;

import lombok.Getter;

/**
 * 요청 빈도 제한 초과(#23).
 *
 * {@link GeneralException}을 상속하므로 별도 처리기가 없어도 429로 나간다. 다만 전용 처리기를
 * 두어 {@code Retry-After} 헤더를 붙인다 — 클라이언트가 언제 다시 시도할지 알아야 무의미한
 * 재시도로 한도를 더 깎지 않는다.
 */
@Getter
public class RateLimitExceededException extends GeneralException {
    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(GeneralErrorCode.TOO_MANY_REQUESTS);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
