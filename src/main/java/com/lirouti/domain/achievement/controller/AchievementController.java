package com.lirouti.domain.achievement.controller;

import com.lirouti.domain.achievement.controller.docs.AchievementControllerDocs;
import com.lirouti.domain.achievement.dto.request.AchievementReqDTO;
import com.lirouti.domain.achievement.dto.response.AchievementResDTO;
import com.lirouti.domain.achievement.exception.code.success.AchievementSuccessCode;
import com.lirouti.domain.achievement.service.AchievementClaimService;
import com.lirouti.domain.achievement.service.command.RepresentativeAchievementCommandService;
import com.lirouti.domain.achievement.service.command.WaveRoutineCommandService;
import com.lirouti.domain.achievement.service.query.AchievementQueryService;
import com.lirouti.domain.achievement.service.query.RepresentativeAchievementQueryService;
import com.lirouti.domain.achievement.service.query.WaveRoutineQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/achievements")
public class AchievementController implements AchievementControllerDocs {

    private final AchievementQueryService achievementQueryService;
    private final AchievementClaimService achievementClaimService;

    private final WaveRoutineCommandService waveRoutineCommandService;
    private final WaveRoutineQueryService waveRoutineQueryService;

    private final RepresentativeAchievementCommandService representativeAchievementCommandService;
    private final RepresentativeAchievementQueryService representativeAchievementQueryService;

    @Override
    @GetMapping
    public ApiResponse<AchievementResDTO.Achievements> getAchievements(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        AchievementResDTO.Achievements result =
                achievementQueryService.getMyAchievements(userDetails.getMemberId());
        return ApiResponse.onSuccess(AchievementSuccessCode.ACHIEVEMENT_LIST_FETCH_SUCCESS, result);
    }

    @PostMapping("/{achievementId}/claim")
    @Override
    public ApiResponse<AchievementResDTO.Claim> claim(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long achievementId
    ) {
        AchievementClaimService.ClaimResult result =
                achievementClaimService.claim(userDetails.getMemberId(), achievementId);

        AchievementResDTO.Claim response = AchievementResDTO.Claim.builder()
                .achievementId(result.achievementId())
                .freeBalanceAfter(result.freeBalanceAfter())
                .rewardApplied(result.rewardApplied())
                .build();

        return ApiResponse.onSuccess(AchievementSuccessCode.ACHIEVEMENT_CLAIM_SUCCESS, response);
    }

    @PutMapping("/wave-routine")
    public ApiResponse<Void> selectWaveRoutine(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AchievementReqDTO.SelectWaveRoutine request
    ) {
        waveRoutineCommandService.selectRoutine(userDetails.getMemberId(), request.memberRoutineId());
        return ApiResponse.onSuccess(AchievementSuccessCode.WAVE_ROUTINE_SELECT_SUCCESS, null);
    }

    @GetMapping("/wave-routine")
    public ApiResponse<AchievementResDTO.WaveRoutineStatus> getWaveRoutineStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        AchievementResDTO.WaveRoutineStatus result = waveRoutineQueryService.getStatus(userDetails.getMemberId());
        return ApiResponse.onSuccess(AchievementSuccessCode.WAVE_ROUTINE_FETCH_SUCCESS, result);
    }

    @PutMapping("/representative")
    public ApiResponse<Void> selectRepresentative(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AchievementReqDTO.SelectRepresentativeAchievement request
    ) {
        representativeAchievementCommandService.select(userDetails.getMemberId(), request.achievementId());
        return ApiResponse.onSuccess(AchievementSuccessCode.REPRESENTATIVE_SELECT_SUCCESS, null);
    }

    @DeleteMapping("/representative")
    public ApiResponse<Void> clearRepresentative(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        representativeAchievementCommandService.clear(userDetails.getMemberId());
        return ApiResponse.onSuccess(AchievementSuccessCode.REPRESENTATIVE_CLEAR_SUCCESS, null);
    }

    @GetMapping("/representative/selectable")
    public ApiResponse<AchievementResDTO.ClaimedBadgeAchievements> getSelectableRepresentative(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        AchievementResDTO.ClaimedBadgeAchievements result =
                representativeAchievementQueryService.getSelectableBadges(userDetails.getMemberId());
        return ApiResponse.onSuccess(AchievementSuccessCode.REPRESENTATIVE_LIST_FETCH_SUCCESS, result);
    }
}
