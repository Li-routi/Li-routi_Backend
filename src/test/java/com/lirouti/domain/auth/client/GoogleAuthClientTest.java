package com.lirouti.domain.auth.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.auth.oauth2.TokenVerifier;
import com.lirouti.domain.auth.exception.AuthException;
import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.global.properties.GoogleOAuthProperties;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoogleAuthClient 테스트")
class GoogleAuthClientTest {
    private static final String TOKEN = "provider-token";

    @Mock
    private TokenVerifier tokenVerifier;

    @Test
    @DisplayName("잘못된 JWT 파싱 실패는 INVALID_SOCIAL_TOKEN으로 처리한다")
    void authenticate_MalformedToken_ThrowsInvalidSocialToken() throws Exception {
        // given
        GoogleAuthClient client = createClient();
        when(tokenVerifier.verify(TOKEN)).thenThrow(
                new TokenVerifier.VerificationException("parse failed", new IOException("malformed token")));

        // when & then
        assertThatThrownBy(() -> client.authenticate(TOKEN, null))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCode.INVALID_SOCIAL_TOKEN);
    }

    @Test
    @DisplayName("공개키 조회 실패는 SOCIAL_COMMUNICATION_ERROR로 처리한다")
    void authenticate_PublicKeyFetchFailure_ThrowsCommunicationError() throws Exception {
        // given
        GoogleAuthClient client = createClient();
        when(tokenVerifier.verify(TOKEN)).thenThrow(
                new TokenVerifier.VerificationException(
                        "key fetch failed",
                        new ExecutionException(new IOException("network error"))));

        // when & then
        assertThatThrownBy(() -> client.authenticate(TOKEN, null))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCode.SOCIAL_COMMUNICATION_ERROR);
    }

    private GoogleAuthClient createClient() {
        GoogleOAuthProperties properties = new GoogleOAuthProperties();
        properties.setWebClientId("web-client-id");
        properties.setAllowedIssuers(Set.of("accounts.google.com"));

        GoogleAuthClient client = new GoogleAuthClient(properties);
        ReflectionTestUtils.setField(client, "verifiers", List.of(tokenVerifier));
        return client;
    }
}
