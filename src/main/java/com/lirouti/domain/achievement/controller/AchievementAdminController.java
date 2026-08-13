package com.lirouti.domain.achievement.controller;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lirouti.domain.achievement.controller.docs.AchievementAdminControllerDocs;
import com.lirouti.domain.achievement.dto.response.AchievementResDTO;
import com.lirouti.domain.achievement.exception.code.success.AchievementSuccessCode;
import com.lirouti.domain.achievement.service.AchievementBadgeImageAdminService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/achievements")
public class AchievementAdminController implements AchievementAdminControllerDocs {

    private final AchievementBadgeImageAdminService achievementBadgeImageAdminService;

    /** 업적 뱃지 이미지를 새 object로 등록하거나 기존 이미지를 교체한다. */
    @Override
    @PutMapping(value = "/{achievementId}/badge-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AchievementResDTO.AdminBadgeImage> uploadBadgeImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long achievementId,
            @RequestPart("file") MultipartFile file
    ) {
        AchievementResDTO.AdminBadgeImage result =
                achievementBadgeImageAdminService.uploadBadgeImage(
                        userDetails.getMemberId(),
                        achievementId,
                        file.getContentType(),
                        file.getSize(),
                        file
                );
        return ApiResponse.onSuccess(
                AchievementSuccessCode.ACHIEVEMENT_BADGE_IMAGE_UPLOAD_SUCCESS,
                result
        );
    }
}
