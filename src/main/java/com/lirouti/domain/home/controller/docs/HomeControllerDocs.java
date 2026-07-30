package com.lirouti.domain.home.controller.docs;

import com.lirouti.domain.home.dto.response.HomeResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Home", description = "홈 화면 API")
public interface HomeControllerDocs {

    @Operation(
            summary = "홈 화면 요약 정보 조회",
            description = """
                    로그인 회원 정보 및 오늘 할당된 개인 루틴과 그룹 루틴 목록을 통합 조회합니다.
                    인증 토큰의 회원 정보를 기반으로 처리됩니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "홈 화면 요약 정보 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않거나 만려된 인증 토큰"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원 정보를 찾을 수 없음"
            )
    })
    ApiResponse<HomeResDTO.MainSummary> getHomeSummary(
            @Parameter(hidden = true) CustomUserDetails userDetails
    );
}
