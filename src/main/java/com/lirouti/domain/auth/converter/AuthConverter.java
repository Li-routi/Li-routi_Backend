package com.lirouti.domain.auth.converter;

import com.lirouti.domain.auth.dto.response.AuthResDTO;

public final class AuthConverter {
    private AuthConverter() {
    }

    public static AuthResDTO.GoogleNonce toGoogleNonce(String nonce) {
        return AuthResDTO.GoogleNonce.builder()
                .nonce(nonce)
                .build();
    }

    public static AuthResDTO.Token toToken(
            String accessToken,
            String refreshToken,
            long accessTokenExpiresIn,
            boolean onboardingCompleted
    ) {
        return AuthResDTO.Token.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(accessTokenExpiresIn)
                .onboardingCompleted(onboardingCompleted)
                .build();
    }
}
