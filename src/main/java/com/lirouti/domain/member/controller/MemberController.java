package com.lirouti.domain.member.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lirouti.domain.auth.exception.AuthException;
import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.domain.member.controller.docs.MemberControllerDocs;
import com.lirouti.domain.member.exception.code.success.MemberSuccessCode;
import com.lirouti.domain.member.service.command.MemberCommandService;
import com.lirouti.global.apiPayload.ApiResponse;

import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberController implements MemberControllerDocs {
    private final MemberCommandService memberCommandService;

    @Override
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION)
        String authorization
    ) {
        memberCommandService.logout(extractBearerToken(authorization));
        return ApiResponse.onSuccess(MemberSuccessCode.MEMBER_LOGOUT_SUCCESS, null);
    }

    // Authorization 헤더에서 Bearer 토큰을 추출하고 유효성을 검사
    private String extractBearerToken(String authorization) {
        if (authorization == null || !StringUtils.hasText(authorization)
                || !authorization.startsWith("Bearer ")) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }

        String accessToken = authorization.substring(7).trim();
        if (!StringUtils.hasText(accessToken)) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }

        return accessToken;
    }
}
