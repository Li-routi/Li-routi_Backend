package com.lirouti.domain.achievement.controller.docs;

import com.lirouti.domain.achievement.dto.request.AchievementReqDTO;
import com.lirouti.domain.achievement.dto.response.AchievementResDTO;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Tag(name = "Achievement", description = "업적 API")
public interface AchievementControllerDocs {

    @Operation(
            summary = "업적 목록 조회",
            description = """
                    마이 > 업적 화면에 필요한 데이터를 통합 조회합니다.
                    카테고리(rare, epic, unique)별로 묶인 전체 업적 목록과, 획득/진행 중 개수를 담은
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

    @PostMapping("/{achievementId}/claim")
    ApiResponse<AchievementResDTO.Claim> claim(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long achievementId
    );

    @Operation(
            summary = "대표 업적 선택",
            description = """
                마이 > 업적 화면의 "달성" 탭에서 대표 업적을 선택합니다.
                배지 이미지가 등록되어 있고, 본인이 실제로 CLAIMED(보상 수령 완료)한
                업적만 선택할 수 있습니다. 이미 다른 업적이 대표로 설정되어 있으면
                이번에 선택한 업적으로 덮어씁니다(한 번에 하나만 유지).
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "대표 업적 선택 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "배지 이미지가 없는 업적을 선택 시도"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않거나 만료된 인증 토큰"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 업적이거나 회원의 진행 기록이 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "아직 보상을 수령(CLAIMED)하지 않은 업적을 선택 시도"
            )
    })
    ApiResponse<Void> selectRepresentative(
            @Parameter(hidden = true) CustomUserDetails userDetails,
            AchievementReqDTO.SelectRepresentativeAchievement request
    );

    @Operation(
            summary = "대표 업적 해제",
            description = """
                설정되어 있던 대표 업적을 해제합니다. 이미 해제된 상태에서 다시
                호출해도 안전합니다(멱등) — 그냥 값이 비어있는 상태를 유지합니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "대표 업적 해제 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않거나 만료된 인증 토큰"
            )
    })
    ApiResponse<Void> clearRepresentative(
            @Parameter(hidden = true) CustomUserDetails userDetails
    );

    @Operation(
            summary = "대표 업적 선택 가능 목록 조회",
            description = """
                마이 > 업적 화면 "달성" 탭에 노출할, 대표 업적으로 선택 가능한
                업적 목록을 조회합니다. 배지 이미지가 등록되어 있고 CLAIMED된
                업적만 반환하며, 현재 대표로 설정된 업적에는 representative=true가
                표시됩니다.
                """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "선택 가능한 대표 업적 목록 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않거나 만료된 인증 토큰"
            )
    })
    ApiResponse<AchievementResDTO.ClaimedBadgeAchievements> getSelectableRepresentative(
            @Parameter(hidden = true) CustomUserDetails userDetails
    );
}
