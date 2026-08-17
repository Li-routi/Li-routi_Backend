package com.lirouti.domain.achievement.controller;

import com.lirouti.domain.achievement.controller.docs.AchievementControllerDocs;
import com.lirouti.domain.achievement.dto.response.AchievementResDTO;
import com.lirouti.domain.achievement.exception.code.success.AchievementSuccessCode;
import com.lirouti.domain.achievement.service.AchievementClaimService;
import com.lirouti.domain.achievement.service.query.AchievementQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/achievements")
public class AchievementController implements AchievementControllerDocs {

    private final AchievementQueryService achievementQueryService;
    private final AchievementClaimService achievementClaimService;

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
}
