package com.lirouti.domain.member.controller.docs;

import com.lirouti.domain.member.dto.request.MemberReqDTO;
import com.lirouti.domain.member.dto.response.MemberResDTO;
import com.lirouti.global.auth.CustomUserDetails;
import com.lirouti.global.apiPayload.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Member", description = "회원 API")
public interface MemberControllerDocs {

    @Operation(
            summary = "내 정보 조회",
            description = "로그인한 회원의 프로필 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "회원 정보 조회 성공"
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
    ApiResponse<MemberResDTO.MemberInfo> getMyInfo(
            @Parameter(hidden = true) CustomUserDetails userDetails
    );

    @Operation(
            summary = "프로필 수정",
            description = "로그인한 회원의 프로필 정보를 수정합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "프로필 수정 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 값 유효성 검증 실패"
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
    ApiResponse<MemberResDTO.MemberInfo> updateProfile(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            MemberReqDTO.UpdateProfile request
    );

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
    ApiResponse<Void> logout(String authorization);

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
            @Parameter(hidden = true) CustomUserDetails userDetails,
            MemberReqDTO.Withdraw request
    );
}
