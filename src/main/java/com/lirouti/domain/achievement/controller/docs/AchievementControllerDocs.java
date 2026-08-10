package com.lirouti.domain.achievement.controller.docs;

import com.lirouti.domain.achievement.dto.response.AchievementResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Achievement", description = "업적 API")
public interface AchievementControllerDocs {

    @Operation(
            summary = "업적 목록 조회",
            description = """
                    마이 > 업적 화면에 필요한 데이터를 통합 조회합니다.
                    카테고리(시작·달성·스페셜)별로 묶인 전체 업적 목록과, 획득/진행 중 개수를 담은
                    상단 요약을 함께 내려줍니다. 아직 진행도가 없는 업적도 목록에 포함되며,
                    이 경우 진행 상태는 IN_PROGRESS·진행도 0으로 표시됩니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "업적 목록 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않거나 만료된 인증 토큰"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "회원 정보를 찾을 수 없음"
            )
    })
    ApiResponse<AchievementResDTO.Achievements> getAchievements(
            @Parameter(hidden = true) CustomUserDetails userDetails
    );
}
