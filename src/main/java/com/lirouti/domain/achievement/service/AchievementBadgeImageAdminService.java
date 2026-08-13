package com.lirouti.domain.achievement.service;

import com.lirouti.domain.achievement.dto.response.AchievementResDTO;
import com.lirouti.domain.achievement.exception.AchievementException;
import com.lirouti.domain.achievement.exception.code.error.AchievementErrorCode;
import com.lirouti.domain.achievement.repository.AchievementRepository;
import com.lirouti.domain.achievement.service.command.AchievementCommandService;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.global.apiPayload.code.GeneralErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementBadgeImageAdminService {
    private static final MediaPurpose BADGE_PURPOSE = MediaPurpose.ACHIEVEMENT_BADGE;

    private final MemberQueryService memberQueryService;
    private final AchievementRepository achievementRepository;
    private final MediaService mediaService;
    private final AchievementCommandService achievementCommandService;

    /**
     * 활성 관리자만 업적 뱃지를 등록하거나 교체한다.
     * S3 처리와 DB 변경을 분리하고, DB 변경 전 실패한 새 object는 보상 삭제한다.
     */
    public AchievementResDTO.AdminBadgeImage uploadBadgeImage(
            Long adminId,
            Long achievementId,
            String fileContentType,
            long contentLength,
            InputStreamSource contentSource
    ) {
        validateActiveAdmin(adminId);
        if (achievementId == null) {
            throw new AchievementException(AchievementErrorCode.ACHIEVEMENT_ID_REQUIRED);
        }
        achievementRepository.findById(achievementId)
                .orElseThrow(() -> new AchievementException(AchievementErrorCode.NOT_FOUND));

        MediaService.UploadedMedia uploaded = mediaService.uploadServiceOwnedImage(
                BADGE_PURPOSE,
                fileContentType,
                fileContentType,
                contentLength,
                contentSource
        );

        AchievementCommandService.BadgeImageUpdate update;
        String badgeImageUrl;
        try {
            badgeImageUrl = mediaService.resolveViewUrl(uploaded.mediaKey(), BADGE_PURPOSE);
            update = achievementCommandService.replaceBadgeImageKey(
                    achievementId,
                    uploaded.mediaKey()
            );
        } catch (RuntimeException originalFailure) {
            compensateUploadedMedia(uploaded.mediaKey(), originalFailure);
            throw originalFailure;
        }

        deletePreviousImageAfterCommit(update.previousBadgeImageKey());
        return AchievementResDTO.AdminBadgeImage.builder()
                .achievementId(update.achievementId())
                .code(update.achievementCode())
                .badgeImageUrl(badgeImageUrl)
                .build();
    }

    private void validateActiveAdmin(Long adminId) {
        Member member = memberQueryService.getActiveMember(adminId);
        if (member.getRole() != Role.ROLE_ADMIN) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
    }

    private void compensateUploadedMedia(String mediaKey, RuntimeException originalFailure) {
        try {
            mediaService.deleteServiceOwnedMedia(mediaKey, BADGE_PURPOSE);
        } catch (RuntimeException compensationFailure) {
            if (compensationFailure != originalFailure) {
                originalFailure.addSuppressed(compensationFailure);
            }
            log.error("업적 뱃지 등록 실패 후 S3 보상 삭제에도 실패했습니다. mediaKey={}",
                    mediaKey, compensationFailure);
        }
    }

    private void deletePreviousImageAfterCommit(String previousBadgeImageKey) {
        if (previousBadgeImageKey == null) {
            return;
        }
        try {
            mediaService.deleteServiceOwnedMedia(previousBadgeImageKey, BADGE_PURPOSE);
        } catch (RuntimeException deletionFailure) {
            log.error("업적 뱃지 교체 후 기존 S3 object 삭제에 실패했습니다. mediaKey={}",
                    previousBadgeImageKey, deletionFailure);
        }
    }
}
