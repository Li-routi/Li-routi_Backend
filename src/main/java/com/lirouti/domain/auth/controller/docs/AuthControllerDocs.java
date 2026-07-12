package com.lirouti.domain.auth.controller.docs;

import com.lirouti.domain.auth.dto.request.AuthReqDTO;
import com.lirouti.domain.auth.dto.response.AuthResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Auth", description = "인증 API")
public interface AuthControllerDocs {

    @Operation(
            summary = "Google 로그인 nonce 발급",
            description = "Google ID Token 재사용 공격 방지를 위한 일회용 nonce를 발급합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "nonce 발급 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "서버 내부 오류"
        )
    })
    ApiResponse<AuthResDTO.GoogleNonce> issueGoogleNonce();

    @Operation(
            summary = "토큰 재발급",
            description = "Refresh Token을 검증하고 새로운 Access Token과 Refresh Token을 발급합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "토큰 재발급 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청 값 오류"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "토큰 검증 실패"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "탈퇴 또는 비활성 회원"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "회원 조회 실패"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "서버 내부 오류"
        )
    })
    ApiResponse<AuthResDTO.Token> reissue(AuthReqDTO.Reissue request);

    @Operation(
            summary = "소셜 로그인",
            description = "Kakao Access Token 또는 Google ID Token을 검증하고 서비스 토큰을 발급합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "소셜 로그인 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청 값 오류 또는 지원하지 않는 제공자"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "소셜 토큰 또는 Google nonce 검증 실패"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "탈퇴 또는 비활성 회원"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "다른 소셜 계정에서 사용 중인 이메일"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "422",
                description = "신규 가입에 필요한 이메일 또는 닉네임 누락"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "소셜 서버 통신 또는 서버 내부 오류"
        )
    })
    ApiResponse<AuthResDTO.Token> socialLogin(AuthReqDTO.SocialLogin request);
}
