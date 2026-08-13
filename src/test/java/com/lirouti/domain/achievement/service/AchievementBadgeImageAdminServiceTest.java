package com.lirouti.domain.achievement.service;

import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.exception.AchievementException;
import com.lirouti.domain.achievement.exception.code.error.AchievementErrorCode;
import com.lirouti.domain.achievement.repository.AchievementRepository;
import com.lirouti.domain.achievement.service.command.AchievementCommandService;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.exception.MediaException;
import com.lirouti.domain.media.exception.code.error.MediaErrorCode;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.global.apiPayload.code.GeneralErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AchievementBadgeImageAdminService 테스트")
class AchievementBadgeImageAdminServiceTest {
    private static final Long ADMIN_ID = 1L;
    private static final Long ACHIEVEMENT_ID = 21L;
    private static final String ACHIEVEMENT_CODE = "ACH-AC-001";
    private static final String NEW_KEY =
            "achievement-badges/2026/08/13/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.png";
    private static final String OLD_KEY =
            "achievement-badges/2026/08/12/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb.png";
    private static final String IMAGE_URL = "https://cdn.example.com/achievement.png";
    private static final String CONTENT_TYPE = "image/png";

    @Mock
    private MemberQueryService memberQueryService;
    @Mock
    private AchievementRepository achievementRepository;
    @Mock
    private MediaService mediaService;
    @Mock
    private AchievementCommandService achievementCommandService;
    @Mock
    private Member admin;
    @Mock
    private Achievement achievement;

    @InjectMocks
    private AchievementBadgeImageAdminService adminService;

    @Test
    @DisplayName("활성 관리자는 새 뱃지를 업로드하고 DB 반영 후 기존 이미지를 삭제한다")
    void uploadBadgeImage_Admin_ReplacesImageAndDeletesPreviousKey() {
        InputStreamSource source = source();
        givenActiveAdmin();
        when(achievementRepository.findById(ACHIEVEMENT_ID)).thenReturn(Optional.of(achievement));
        when(mediaService.uploadServiceOwnedImage(
                MediaPurpose.ACHIEVEMENT_BADGE,
                CONTENT_TYPE,
                CONTENT_TYPE,
                10L,
                source
        )).thenReturn(new MediaService.UploadedMedia(NEW_KEY, CONTENT_TYPE));
        when(mediaService.resolveViewUrl(NEW_KEY, MediaPurpose.ACHIEVEMENT_BADGE))
                .thenReturn(IMAGE_URL);
        when(achievementCommandService.replaceBadgeImageKey(ACHIEVEMENT_ID, NEW_KEY))
                .thenReturn(new AchievementCommandService.BadgeImageUpdate(
                        ACHIEVEMENT_ID, ACHIEVEMENT_CODE, OLD_KEY));

        var result = adminService.uploadBadgeImage(
                ADMIN_ID, ACHIEVEMENT_ID, CONTENT_TYPE, 10L, source);

        assertThat(result.achievementId()).isEqualTo(ACHIEVEMENT_ID);
        assertThat(result.code()).isEqualTo(ACHIEVEMENT_CODE);
        assertThat(result.badgeImageUrl()).isEqualTo(IMAGE_URL);
        InOrder order = inOrder(mediaService, achievementCommandService);
        order.verify(mediaService).uploadServiceOwnedImage(
                MediaPurpose.ACHIEVEMENT_BADGE,
                CONTENT_TYPE,
                CONTENT_TYPE,
                10L,
                source
        );
        order.verify(mediaService).resolveViewUrl(NEW_KEY, MediaPurpose.ACHIEVEMENT_BADGE);
        order.verify(achievementCommandService).replaceBadgeImageKey(ACHIEVEMENT_ID, NEW_KEY);
        order.verify(mediaService).deleteServiceOwnedMedia(OLD_KEY, MediaPurpose.ACHIEVEMENT_BADGE);
    }

    @Test
    @DisplayName("현재 DB role이 관리자가 아니면 S3 업로드 전에 403으로 거부한다")
    void uploadBadgeImage_UserRole_ThrowsForbiddenBeforeUpload() {
        when(memberQueryService.getActiveMember(ADMIN_ID)).thenReturn(admin);
        when(admin.getRole()).thenReturn(Role.ROLE_USER);

        assertThatThrownBy(() -> adminService.uploadBadgeImage(
                ADMIN_ID, ACHIEVEMENT_ID, CONTENT_TYPE, 10L, source()))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.FORBIDDEN);

        verifyNoInteractions(achievementRepository, mediaService, achievementCommandService);
    }

    @Test
    @DisplayName("존재하지 않는 업적은 S3 업로드 전에 404로 거부한다")
    void uploadBadgeImage_AchievementNotFound_DoesNotUpload() {
        givenActiveAdmin();
        when(achievementRepository.findById(ACHIEVEMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.uploadBadgeImage(
                ADMIN_ID, ACHIEVEMENT_ID, CONTENT_TYPE, 10L, source()))
                .isInstanceOf(AchievementException.class)
                .extracting("code")
                .isEqualTo(AchievementErrorCode.NOT_FOUND);

        verifyNoInteractions(mediaService, achievementCommandService);
    }

    @Test
    @DisplayName("DB key 교체 실패 시 새 S3 object를 보상 삭제하고 원래 예외를 유지한다")
    void uploadBadgeImage_DatabaseFailure_DeletesNewObject() {
        InputStreamSource source = source();
        RuntimeException originalFailure = new IllegalStateException("database failure");
        givenActiveAdmin();
        when(achievementRepository.findById(ACHIEVEMENT_ID)).thenReturn(Optional.of(achievement));
        when(mediaService.uploadServiceOwnedImage(
                MediaPurpose.ACHIEVEMENT_BADGE,
                CONTENT_TYPE,
                CONTENT_TYPE,
                10L,
                source
        )).thenReturn(new MediaService.UploadedMedia(NEW_KEY, CONTENT_TYPE));
        when(mediaService.resolveViewUrl(NEW_KEY, MediaPurpose.ACHIEVEMENT_BADGE))
                .thenReturn(IMAGE_URL);
        when(achievementCommandService.replaceBadgeImageKey(ACHIEVEMENT_ID, NEW_KEY))
                .thenThrow(originalFailure);

        assertThatThrownBy(() -> adminService.uploadBadgeImage(
                ADMIN_ID, ACHIEVEMENT_ID, CONTENT_TYPE, 10L, source))
                .isSameAs(originalFailure);

        verify(mediaService).deleteServiceOwnedMedia(NEW_KEY, MediaPurpose.ACHIEVEMENT_BADGE);
    }

    @Test
    @DisplayName("보상 삭제 실패 시에도 원래 DB 예외를 유지하고 suppressed 예외를 남긴다")
    void uploadBadgeImage_CompensationFailure_PreservesOriginalFailure() {
        InputStreamSource source = source();
        AchievementException originalFailure = new AchievementException(AchievementErrorCode.NOT_FOUND);
        MediaException compensationFailure = new MediaException(MediaErrorCode.MEDIA_DELETE_FAILED);
        givenActiveAdmin();
        when(achievementRepository.findById(ACHIEVEMENT_ID)).thenReturn(Optional.of(achievement));
        when(mediaService.uploadServiceOwnedImage(
                MediaPurpose.ACHIEVEMENT_BADGE,
                CONTENT_TYPE,
                CONTENT_TYPE,
                10L,
                source
        )).thenReturn(new MediaService.UploadedMedia(NEW_KEY, CONTENT_TYPE));
        when(mediaService.resolveViewUrl(NEW_KEY, MediaPurpose.ACHIEVEMENT_BADGE))
                .thenReturn(IMAGE_URL);
        when(achievementCommandService.replaceBadgeImageKey(ACHIEVEMENT_ID, NEW_KEY))
                .thenThrow(originalFailure);
        doThrow(compensationFailure)
                .when(mediaService)
                .deleteServiceOwnedMedia(NEW_KEY, MediaPurpose.ACHIEVEMENT_BADGE);

        assertThatThrownBy(() -> adminService.uploadBadgeImage(
                ADMIN_ID, ACHIEVEMENT_ID, CONTENT_TYPE, 10L, source))
                .isSameAs(originalFailure);
        assertThat(originalFailure.getSuppressed()).containsExactly(compensationFailure);
    }

    private void givenActiveAdmin() {
        when(memberQueryService.getActiveMember(ADMIN_ID)).thenReturn(admin);
        when(admin.getRole()).thenReturn(Role.ROLE_ADMIN);
    }

    private InputStreamSource source() {
        return () -> new java.io.ByteArrayInputStream(new byte[10]);
    }
}
