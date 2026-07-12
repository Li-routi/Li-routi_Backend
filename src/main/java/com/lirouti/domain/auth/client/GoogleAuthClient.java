package com.lirouti.domain.auth.client;

import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.auth.oauth2.TokenVerifier;
import com.lirouti.domain.auth.exception.AuthException;
import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.domain.auth.model.SocialUserInfo;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.global.properties.GoogleOAuthProperties;
import java.util.concurrent.ExecutionException;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GoogleAuthClient implements SocialAuthClient {
    private final List<TokenVerifier> verifiers;

    public GoogleAuthClient(GoogleOAuthProperties googleOAuthProperties) {
        // 허용 issuer별 verifier 구성
        this.verifiers = googleOAuthProperties.getAllowedIssuers().stream()
                .map(issuer -> TokenVerifier.newBuilder()
                        .setAudience(googleOAuthProperties.getWebClientId())
                        .setIssuer(issuer)
                        .build())
                .toList();
    }

    @Override
    public boolean supports(SocialProvider provider) {
        return provider == SocialProvider.GOOGLE;
    }

    @Override
    public SocialUserInfo authenticate(String providerToken, String nonce) {
        // 서명·audience·issuer·만료 검증 및 payload 추출
        JsonWebToken.Payload payload = verify(providerToken);
        // replay 공격 방지용 nonce claim 일치 검증
        validateNonce(nonce, payload);
        log.debug("Google ID Token 검증에 성공했습니다.");

        return new SocialUserInfo(
                SocialProvider.GOOGLE,
                getRequiredSubject(payload),
                getVerifiedEmail(payload),
                getName(payload)
        );
    }

    private JsonWebToken.Payload verify(String providerToken) {
        // 각 verifier를 순회하며 검증 시도
        for (TokenVerifier verifier : verifiers) {
            try {
                JsonWebSignature idToken = verifier.verify(providerToken);
                return idToken.getPayload();
            } catch (TokenVerifier.VerificationException e) {
                // 공개키 조회 실패만 통신 오류로 분류하고, 파싱 실패는 토큰 오류로 남김
                if (isCausedBy(e, ExecutionException.class)
                        || isCausedBy(e, UncheckedExecutionException.class)) {
                    log.error("Google 공개키 조회 중 통신 오류가 발생했습니다.");
                    throw new AuthException(AuthErrorCode.SOCIAL_COMMUNICATION_ERROR);
                }
            }
        }
        // 모든 verifier에서 검증 실패 시 예외 발생
        log.warn("Google ID Token 검증에 실패했습니다.");
        throw new AuthException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
    }

    private boolean isCausedBy(Throwable exception, Class<? extends Throwable> expectedType) {
        // 예외 체인을 순회하며 특정 타입의 원인 예외 존재 여부 확인
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (expectedType.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    private void validateNonce(String nonce, JsonWebToken.Payload payload) {
        if (nonce == null || !nonce.equals(payload.get("nonce"))) {
            log.warn("Google ID Token nonce 검증에 실패했습니다.");
            throw new AuthException(AuthErrorCode.INVALID_GOOGLE_NONCE);
        }
    }

    private String getRequiredSubject(JsonWebToken.Payload payload) {
        // 소셜 회원 식별용 sub claim 필수 검증
        String subject = payload.getSubject();
        if (subject == null || subject.isBlank()) {
            log.warn("Google ID Token에 필수 subject가 없습니다.");
            throw new AuthException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
        }
        return subject;
    }

    private String getVerifiedEmail(JsonWebToken.Payload payload) {
        // 가입에 사용할 검증 완료 이메일 선별
        if (!Boolean.TRUE.equals(payload.get("email_verified"))) {
            return null;
        }
        return getStringClaim(payload, "email");
    }

    private String getName(JsonWebToken.Payload payload) {
        return getStringClaim(payload, "name");
    }

    private String getStringClaim(JsonWebToken.Payload payload, String claimName) {
        Object value = payload.get(claimName);
        return value instanceof String stringValue ? stringValue : null;
    }
}
