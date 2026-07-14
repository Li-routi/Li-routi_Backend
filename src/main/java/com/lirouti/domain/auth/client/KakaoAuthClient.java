package com.lirouti.domain.auth.client;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lirouti.domain.auth.exception.AuthException;
import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.domain.auth.model.SocialUserInfo;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.global.properties.KakaoOAuthProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoAuthClient implements SocialAuthClient {
    private static final String ACCESS_TOKEN_INFO_URI = "https://kapi.kakao.com/v1/user/access_token_info";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;
    private final KakaoOAuthProperties kakaoOAuthProperties;

    @Override
    public boolean supports(SocialProvider provider) {
        return provider == SocialProvider.KAKAO;
    }

    @Override
    public SocialUserInfo authenticate(String providerToken, String nonce) {
        KakaoTokenInfo tokenInfo = getAccessTokenInfo(providerToken);
        validateTokenInfo(tokenInfo);

        KakaoUserInfo userInfo = getUserInfo(providerToken);
        validateUserInfo(tokenInfo, userInfo);
        log.debug("Kakao Access Token 검증에 성공했습니다.");

        KakaoAccount kakaoAccount = userInfo.kakaoAccount();
        return new SocialUserInfo(
                SocialProvider.KAKAO,
                String.valueOf(userInfo.id()),
                getVerifiedEmail(kakaoAccount),
                getNickname(kakaoAccount)
        );
    }

    private KakaoTokenInfo getAccessTokenInfo(String providerToken) {
        return callKakaoApi(
                ACCESS_TOKEN_INFO_URI,
                providerToken,
                KakaoTokenInfo.class,
                "Kakao access-token-info API 통신에 실패했습니다."
        );
    }

    private KakaoUserInfo getUserInfo(String providerToken) {
        return callKakaoApi(
                USER_INFO_URI,
                providerToken,
                KakaoUserInfo.class,
                "Kakao user-me API 통신에 실패했습니다."
            );
    }

    private <T> T callKakaoApi(
        String uri,
         String providerToken,
        Class<T> responseType,
        String failureLogMsg
    ) {
        try {
            return restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientResponseException e) {
            throw mapResponseException(e);
        } catch (RestClientException e) {
            log.error(failureLogMsg, e);
            throw new AuthException(AuthErrorCode.SOCIAL_COMMUNICATION_ERROR);
        }
    } 

    private void validateTokenInfo(KakaoTokenInfo tokenInfo) {
        if (tokenInfo == null
                || tokenInfo.id() == null
                || tokenInfo.expiresIn() == null
                || tokenInfo.expiresIn() <= 0
                || tokenInfo.appId() == null
                || !kakaoOAuthProperties.getAppId().equals(tokenInfo.appId().longValue())) {
            log.warn("Kakao token-info 검증에 실패했습니다.");
            throw new AuthException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
        }
    }

    private void validateUserInfo(KakaoTokenInfo tokenInfo, KakaoUserInfo userInfo) {
        if (userInfo == null || userInfo.id() == null || !tokenInfo.id().equals(userInfo.id())) {
            log.warn("Kakao token-info와 user-me의 사용자 정보가 일치하지 않습니다.");
            throw new AuthException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
        }
    }

    private String getVerifiedEmail(KakaoAccount kakaoAccount) {
        // 이메일 검증 완료 여부 확인
        if (kakaoAccount == null
                || !Boolean.TRUE.equals(kakaoAccount.emailValid())
                || !Boolean.TRUE.equals(kakaoAccount.emailVerified())
                || Boolean.TRUE.equals(kakaoAccount.emailNeedsAgreement())) {
            return null;
        }
        return kakaoAccount.email();
    }

    private String getNickname(KakaoAccount kakaoAccount) {
        if (kakaoAccount == null
                || Boolean.TRUE.equals(kakaoAccount.profileNeedsAgreement())
                || Boolean.TRUE.equals(kakaoAccount.profileNicknameNeedsAgreement())
                || kakaoAccount.profile() == null) {
            return null;
        }
        return kakaoAccount.profile().nickname();
    }

    private AuthException mapResponseException(RestClientResponseException exception) {
        if (exception.getStatusCode().value() == 400 || exception.getStatusCode().value() == 401) {
            log.warn("Kakao API가 소셜 토큰을 거부했습니다. status={}", exception.getStatusCode().value());
            return new AuthException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
        }
        log.error("Kakao API 응답 오류가 발생했습니다. status={}", exception.getStatusCode().value());
        return new AuthException(AuthErrorCode.SOCIAL_COMMUNICATION_ERROR);
    }

    // Kakao API 응답 구조를 반영한 내부 DTO 클래스들
    private record KakaoTokenInfo(
            Long id,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("app_id") Long appId
    ) {
    }

    private record KakaoUserInfo(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount
    ) {
    }

    private record KakaoAccount(
            String email,
            @JsonProperty("is_email_valid") Boolean emailValid,
            @JsonProperty("is_email_verified") Boolean emailVerified,
            @JsonProperty("email_needs_agreement") Boolean emailNeedsAgreement,
            @JsonProperty("profile_needs_agreement") Boolean profileNeedsAgreement,
            @JsonProperty("profile_nickname_needs_agreement") Boolean profileNicknameNeedsAgreement,
            KakaoProfile profile
    ) {
    }

    private record KakaoProfile(String nickname) {
    }
}
