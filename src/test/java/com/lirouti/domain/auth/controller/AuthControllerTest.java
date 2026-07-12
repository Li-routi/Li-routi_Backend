package com.lirouti.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lirouti.domain.auth.dto.request.AuthReqDTO;
import com.lirouti.domain.auth.dto.response.AuthResDTO;
import com.lirouti.domain.auth.exception.code.success.AuthSuccessCode;
import com.lirouti.domain.auth.service.AuthService;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.global.apiPayload.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController 테스트")
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    @DisplayName("Google nonce 발급 시 성공 응답을 반환한다")
    void issueGoogleNonce_Success() {
        // given
        AuthResDTO.GoogleNonce serviceResponse = AuthResDTO.GoogleNonce.builder()
                .nonce("nonce")
                .build();
        when(authService.issueGoogleNonce()).thenReturn(serviceResponse);

        ApiResponse<AuthResDTO.GoogleNonce> response = authController.issueGoogleNonce();

        assertThat(response.getIsSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(AuthSuccessCode.GOOGLE_NONCE_ISSUE_SUCCESS.getCode());
        assertThat(response.getMessage())
                .isEqualTo(AuthSuccessCode.GOOGLE_NONCE_ISSUE_SUCCESS.getMessage());
        assertThat(response.getResult()).isSameAs(serviceResponse);
        verify(authService).issueGoogleNonce();
    }

    @Test
    @DisplayName("소셜 로그인 성공 시 로그인 성공 응답을 반환한다")
    void socialLogin_Success() {
        // given
        AuthReqDTO.SocialLogin request = new AuthReqDTO.SocialLogin(
                SocialProvider.KAKAO,
                "provider-token",
                null
        );
        AuthResDTO.Token serviceResponse = AuthResDTO.Token.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .accessTokenExpiresIn(3600L)
                .onboardingCompleted(false)
                .build();
        when(authService.socialLogin(request)).thenReturn(serviceResponse);

        // when
        ApiResponse<AuthResDTO.Token> response = authController.socialLogin(request);

        // then
        assertThat(response.getIsSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(AuthSuccessCode.LOGIN_SUCCESS.getCode());
        assertThat(response.getMessage()).isEqualTo(AuthSuccessCode.LOGIN_SUCCESS.getMessage());
        assertThat(response.getResult()).isSameAs(serviceResponse);
        verify(authService).socialLogin(request);
    }

    @Test
    @DisplayName("토큰 재발급 성공 시 재발급 성공 응답을 반환한다")
    void reissue_Success() {
        // given
        AuthReqDTO.Reissue request = new AuthReqDTO.Reissue("refresh-token");
        AuthResDTO.Token serviceResponse = AuthResDTO.Token.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .accessTokenExpiresIn(3600L)
                .onboardingCompleted(false)
                .build();
        when(authService.reissue(request)).thenReturn(serviceResponse);

        // when
        ApiResponse<AuthResDTO.Token> response = authController.reissue(request);

        // then
        assertThat(response.getIsSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(AuthSuccessCode.TOKEN_REFRESH_SUCCESS.getCode());
        assertThat(response.getMessage()).isEqualTo(AuthSuccessCode.TOKEN_REFRESH_SUCCESS.getMessage());
        assertThat(response.getResult()).isSameAs(serviceResponse);
        verify(authService).reissue(request);
    }
}
