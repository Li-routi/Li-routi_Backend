package com.lirouti.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lirouti.domain.auth.client.SocialAuthClient;
import com.lirouti.domain.auth.client.SocialAuthClientFactory;
import com.lirouti.domain.auth.dto.request.AuthReqDTO;
import com.lirouti.domain.auth.dto.response.AuthResDTO;
import com.lirouti.domain.auth.exception.AuthException;
import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.domain.auth.model.SocialUserInfo;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.service.command.MemberCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 테스트")
class AuthServiceTest {
    private static final String PROVIDER_TOKEN = "provider-token";
    private static final String NONCE = "raw-nonce";

    @Mock
    private GoogleNonceService googleNonceService;
    @Mock
    private SocialAuthClientFactory socialAuthClientFactory;
    @Mock
    private MemberCommandService memberCommandService;
    @Mock
    private TokenService tokenService;
    @Mock
    private SocialAuthClient socialAuthClient;
    @Mock
    private Member member;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Google 소셜 로그인 성공 시 nonce 소비 후 회원 처리와 토큰 발급을 수행한다")
    void socialLogin_GoogleSuccess_ConsumesNonceAfterProviderAuthentication() {
        // given
        AuthReqDTO.SocialLogin request = new AuthReqDTO.SocialLogin(
                SocialProvider.GOOGLE, PROVIDER_TOKEN, NONCE);
        SocialUserInfo userInfo = new SocialUserInfo(
                SocialProvider.GOOGLE, "subject", "member@example.com", "member");
        AuthResDTO.Token tokenResponse = AuthResDTO.Token.builder().build();
        when(socialAuthClientFactory.getClient(SocialProvider.GOOGLE)).thenReturn(socialAuthClient);
        when(socialAuthClient.authenticate(PROVIDER_TOKEN, NONCE)).thenReturn(userInfo);
        when(googleNonceService.consumeNonce(NONCE)).thenReturn(true);
        when(memberCommandService.findOrCreateSocialMember(
                userInfo.provider(), userInfo.socialId(), userInfo.email(), userInfo.nickname()))
                .thenReturn(member);
        when(tokenService.issueTokens(member)).thenReturn(tokenResponse);

        // when
        AuthResDTO.Token result = authService.socialLogin(request);

        // then
        assertThat(result).isSameAs(tokenResponse);
        InOrder inOrder = inOrder(socialAuthClient, googleNonceService, memberCommandService, tokenService);
        inOrder.verify(socialAuthClient).authenticate(PROVIDER_TOKEN, NONCE);
        inOrder.verify(googleNonceService).consumeNonce(NONCE);
        inOrder.verify(memberCommandService).findOrCreateSocialMember(
                userInfo.provider(), userInfo.socialId(), userInfo.email(), userInfo.nickname());
        inOrder.verify(tokenService).issueTokens(member);
    }

    @Test
    @DisplayName("Google nonce 소비에 실패하면 회원 처리 전에 예외를 던진다")
    void socialLogin_InvalidGoogleNonce_StopsBeforeMemberProcessing() {
        // given
        AuthReqDTO.SocialLogin request = new AuthReqDTO.SocialLogin(
                SocialProvider.GOOGLE, PROVIDER_TOKEN, NONCE);
        SocialUserInfo userInfo = new SocialUserInfo(
                SocialProvider.GOOGLE, "subject", "member@example.com", "member");
        when(socialAuthClientFactory.getClient(SocialProvider.GOOGLE)).thenReturn(socialAuthClient);
        when(socialAuthClient.authenticate(PROVIDER_TOKEN, NONCE)).thenReturn(userInfo);
        when(googleNonceService.consumeNonce(NONCE)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.socialLogin(request))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCode.INVALID_GOOGLE_NONCE);
        verify(memberCommandService, never()).findOrCreateSocialMember(
                userInfo.provider(), userInfo.socialId(), userInfo.email(), userInfo.nickname());
        verify(tokenService, never()).issueTokens(member);
    }

    @Test
    @DisplayName("Kakao 소셜 로그인 성공 시 Google nonce를 소비하지 않는다")
    void socialLogin_KakaoSuccess_DoesNotConsumeGoogleNonce() {
        // given
        AuthReqDTO.SocialLogin request = new AuthReqDTO.SocialLogin(
                SocialProvider.KAKAO, PROVIDER_TOKEN, null);
        SocialUserInfo userInfo = new SocialUserInfo(
                SocialProvider.KAKAO, "kakao-id", "member@example.com", "member");
        AuthResDTO.Token tokenResponse = AuthResDTO.Token.builder().build();
        when(socialAuthClientFactory.getClient(SocialProvider.KAKAO)).thenReturn(socialAuthClient);
        when(socialAuthClient.authenticate(PROVIDER_TOKEN, null)).thenReturn(userInfo);
        when(memberCommandService.findOrCreateSocialMember(
                userInfo.provider(), userInfo.socialId(), userInfo.email(), userInfo.nickname()))
                .thenReturn(member);
        when(tokenService.issueTokens(member)).thenReturn(tokenResponse);

        // when
        AuthResDTO.Token result = authService.socialLogin(request);

        // then
        assertThat(result).isSameAs(tokenResponse);
        verify(googleNonceService, never()).consumeNonce(null);
    }

    @Test
    @DisplayName("토큰 재발급 요청 시 TokenService로 refresh token을 전달한다")
    void reissue_DelegatesRefreshTokenToTokenService() {
        // given
        AuthReqDTO.Reissue request = new AuthReqDTO.Reissue("refresh-token");
        AuthResDTO.Token tokenResponse = AuthResDTO.Token.builder().build();
        when(tokenService.reissue("refresh-token")).thenReturn(tokenResponse);

        // when
        AuthResDTO.Token result = authService.reissue(request);

        // then
        assertThat(result).isSameAs(tokenResponse);
        verify(tokenService).reissue("refresh-token");
    }
}
