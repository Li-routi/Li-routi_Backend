package com.lirouti.domain.auth.dto.response;

import lombok.Builder;

public final class AuthResDTO {
    private AuthResDTO() {
    }

    @Builder
    public record Token(
            String accessToken,
            String refreshToken,
            Long accessTokenExpiresIn,
            boolean onboardingCompleted
    ) {
    }

    @Builder
    public record GoogleNonce(String nonce) {
    }

    /**
     * 로컬 스웨거 확인용 토큰. 재발급 대상이 아니라 refresh token 을 담지 않는다 —
     * 만료가 14일이라 끊기면 다시 발급받는 편이 빠르다.
     */
    @Builder
    public record DevToken(
            String accessToken,
            Long accessTokenExpiresIn
    ) {
    }
}
