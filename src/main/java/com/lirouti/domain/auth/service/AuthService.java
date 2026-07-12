package com.lirouti.domain.auth.service;

import com.lirouti.domain.auth.converter.AuthConverter;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final GoogleNonceService googleNonceService;
    private final SocialAuthClientFactory socialAuthClientFactory;
    private final MemberCommandService memberCommandService;
    private final TokenService tokenService;

    // Google Nonce 발급
    public AuthResDTO.GoogleNonce issueGoogleNonce() {
        String nonce = googleNonceService.issueNonce();
        return AuthConverter.toGoogleNonce(nonce);
    }

    public AuthResDTO.Token socialLogin(AuthReqDTO.SocialLogin request) {
        SocialAuthClient socialAuthClient = socialAuthClientFactory.getClient(request.provider());
        SocialUserInfo socialUserInfo = socialAuthClient.authenticate(
                request.providerToken(),
                request.nonce()
        );

        consumeGoogleNonce(request);

        Member member = memberCommandService.findOrCreateSocialMember(
                socialUserInfo.provider(),
                socialUserInfo.socialId(),
                socialUserInfo.email(),
                socialUserInfo.nickname()
        );
        log.debug("소셜 로그인 회원 처리를 완료했습니다. memberId={}, provider={}",
                member.getId(), socialUserInfo.provider());
        return tokenService.issueTokens(member);
    }

    public AuthResDTO.Token reissue(AuthReqDTO.Reissue request) {
        return tokenService.reissue(request.refreshToken());
    }

    private void consumeGoogleNonce(AuthReqDTO.SocialLogin request) {
        if (request.provider() != SocialProvider.GOOGLE) {
            return;
        }
        if (!googleNonceService.consumeNonce(request.nonce())) {
            throw new AuthException(AuthErrorCode.INVALID_GOOGLE_NONCE);
        }
    }
}
