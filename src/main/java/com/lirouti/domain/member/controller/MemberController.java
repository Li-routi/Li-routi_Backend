package com.lirouti.domain.member.controller;

import com.lirouti.domain.member.dto.response.MemberResDTO;
import com.lirouti.domain.member.service.query.MemberQueryService;import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import com.lirouti.domain.auth.exception.AuthException;
import com.lirouti.domain.auth.exception.code.error.AuthErrorCode;
import com.lirouti.domain.member.controller.docs.MemberControllerDocs;
import com.lirouti.domain.member.dto.request.MemberReqDTO;
import com.lirouti.domain.member.exception.code.success.MemberSuccessCode;
import com.lirouti.domain.member.service.command.MemberCommandService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberController implements MemberControllerDocs {
    private final MemberCommandService memberCommandService;
    private final MemberQueryService memberQueryService;

    @Override
    @GetMapping("/me")
    public ApiResponse<MemberResDTO.MemberInfo> getMyInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        MemberResDTO.MemberInfo response = memberQueryService.getMemberInfo(userDetails.getMemberId());
        return ApiResponse.onSuccess(MemberSuccessCode.MEMBER_INFO_FETCH_SUCCESS, response);
    }

    @Override
    @PatchMapping("/me/profile")
    public ApiResponse<MemberResDTO.MemberInfo> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody MemberReqDTO.UpdateProfile request
    ) {
        MemberResDTO.MemberInfo response = memberCommandService.updateProfile(userDetails.getMemberId(), request);
        return ApiResponse.onSuccess(MemberSuccessCode.MEMBER_PROFILE_UPDATE_SUCCESS, response);
    }

    @Override
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
        @RequestHeader(HttpHeaders.AUTHORIZATION)
        String authorization
    ) {
        memberCommandService.logout(extractBearerToken(authorization));
        return ApiResponse.onSuccess(MemberSuccessCode.MEMBER_LOGOUT_SUCCESS, null);
    }

    @Override
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(
        @RequestHeader(HttpHeaders.AUTHORIZATION)
        String authorization,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody MemberReqDTO.Withdraw request
    ) {
        memberCommandService.withdraw(
            userDetails.getMemberId(),
            request,
            extractBearerToken(authorization)
        );
        return ApiResponse.onSuccess(MemberSuccessCode.MEMBER_WITHDRAWAL_SUCCESS, null);
    }

    // Authorization 헤더에서 Bearer 토큰을 추출하고 유효성을 검사
    private String extractBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }

        String accessToken = authorization.substring(7).trim();
        if (!StringUtils.hasText(accessToken)) {
            throw new AuthException(AuthErrorCode.TOKEN_INVALID);
        }

        return accessToken;
    }
}
