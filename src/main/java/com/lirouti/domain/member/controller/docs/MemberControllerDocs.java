package com.lirouti.domain.member.controller.docs;

import com.lirouti.domain.member.dto.request.MemberReqDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Member", description = "회원 API")
public interface MemberControllerDocs {

    @Operation(
            summary = "로그아웃",
            description = "현재 access token을 블랙리스트에 등록하고 refresh token 세션을 삭제합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "로그아웃 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "유효하지 않은 토큰"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "인증되지 않은 요청"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "서버 내부 오류"
        )
    })
    ApiResponse<Void> logout(@Parameter(hidden = true) String authorization);

    @Operation(
            summary = "회원 탈퇴",
            description = "회원 정보와 개인 데이터를 탈퇴 처리하고 공동 콘텐츠의 작성자를 익명화합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "회원 탈퇴 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "탈퇴 확인 문구 불일치"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "탈퇴했거나 비활성화된 회원"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "서버 내부 오류"
        )
    })
    ApiResponse<Void> withdraw(
        @Parameter(hidden = true) String authorization,
        @Parameter(hidden = true) CustomUserDetails userDetails,
        MemberReqDTO.Withdraw request
    );
}
