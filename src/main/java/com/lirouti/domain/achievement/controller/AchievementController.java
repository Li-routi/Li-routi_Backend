package com.lirouti.domain.achievement.controller;

import com.lirouti.domain.achievement.controller.docs.AchievementControllerDocs;
import com.lirouti.domain.achievement.dto.response.AchievementResDTO;
import com.lirouti.domain.achievement.exception.code.success.AchievementSuccessCode;
import com.lirouti.domain.achievement.service.query.AchievementQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/achievements")
public class AchievementController implements AchievementControllerDocs {

    private final AchievementQueryService achievementQueryService;

    @Override
    @GetMapping
    public ApiResponse<AchievementResDTO.Achievements> getAchievements(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        AchievementResDTO.Achievements result =
                achievementQueryService.getMyAchievements(userDetails.getMemberId());
        return ApiResponse.onSuccess(AchievementSuccessCode.ACHIEVEMENT_LIST_FETCH_SUCCESS, result);
    }
}
