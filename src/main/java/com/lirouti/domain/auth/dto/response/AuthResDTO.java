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
}
