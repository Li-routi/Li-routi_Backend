package com.lirouti.domain.auth.dto.request;

import com.lirouti.domain.member.enums.SocialProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AuthReqDTO {
    private AuthReqDTO() {
    }

    public record SocialLogin(
            @NotNull(message = "소셜 로그인 제공자는 필수입니다.")
            SocialProvider provider,

            @NotBlank(message = "소셜 로그인 토큰은 필수입니다.")
            @Size(max = 8192, message = "소셜 로그인 토큰은 8192자 이하여야 합니다.")
            String providerToken,

            @Size(max = 512, message = "nonce는 512자 이하여야 합니다.")
            String nonce
    ) {
    }

    public record Reissue(
            @NotBlank(message = "Refresh Token은 필수입니다.")
            @Size(max = 8192, message = "Refresh Token은 8192자 이하여야 합니다.")
            String refreshToken
    ) {
    }
}
