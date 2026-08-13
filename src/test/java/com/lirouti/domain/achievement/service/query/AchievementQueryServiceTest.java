package com.lirouti.domain.achievement.service.query;

import com.lirouti.domain.achievement.dto.response.AchievementResDTO;
import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.enums.AchievementCategory;
import com.lirouti.domain.achievement.enums.AchievementProgressType;
import com.lirouti.domain.achievement.repository.AchievementRepository;
import com.lirouti.domain.achievement.repository.MemberAchievementConditionRepository;
import com.lirouti.domain.achievement.repository.MemberAchievementRepository;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AchievementQueryService 테스트")
class AchievementQueryServiceTest {
    private static final Long MEMBER_ID = 1L;
    private static final String IMAGE_KEY =
            "achievement-badges/2026/08/13/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.png";
    private static final String SECOND_IMAGE_KEY =
            "achievement-badges/2026/08/13/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb.webp";
    private static final String IMAGE_URL = "https://cdn.example.com/achievement.png";
    private static final String SECOND_IMAGE_URL = "https://cdn.example.com/achievement-2.webp";

    @Mock
    private AchievementRepository achievementRepository;
    @Mock
    private MemberAchievementRepository memberAchievementRepository;
    @Mock
    private MemberAchievementConditionRepository memberAchievementConditionRepository;
    @Mock
    private MediaService mediaService;

    @InjectMocks
    private AchievementQueryService achievementQueryService;

    @Test
    @DisplayName("업적별 이미지 key를 URL로 바꾸고 이미지 없는 업적은 null로 반환한다")
    void getMyAchievements_BadgeKeys_ReturnsMappedUrlsAndNullFallback() {
        Achievement withImage = achievement(21L, "ACH-001", IMAGE_KEY, true);
        Achievement withoutImage = achievement(22L, "ACH-002", null, true);
        Achievement nonBadgeRewardWithImage = achievement(23L, "ACH-003", SECOND_IMAGE_KEY, false);
        when(achievementRepository.findAllByActiveTrueOrderByCategoryAscSortOrderAsc())
                .thenReturn(List.of(withImage, withoutImage, nonBadgeRewardWithImage));
        when(memberAchievementRepository.findAllByMemberId(MEMBER_ID)).thenReturn(List.of());
        when(mediaService.resolveViewUrl(IMAGE_KEY, MediaPurpose.ACHIEVEMENT_BADGE))
                .thenReturn(IMAGE_URL);
        when(mediaService.resolveViewUrl(SECOND_IMAGE_KEY, MediaPurpose.ACHIEVEMENT_BADGE))
                .thenReturn(SECOND_IMAGE_URL);

        AchievementResDTO.Achievements result = achievementQueryService.getMyAchievements(MEMBER_ID);

        List<AchievementResDTO.AchievementItem> items = result.categories().get(0).achievements();
        assertThat(items)
                .extracting(AchievementResDTO.AchievementItem::badgeImageUrl)
                .containsExactly(IMAGE_URL, null, SECOND_IMAGE_URL);
        assertThat(items)
                .extracting(AchievementResDTO.AchievementItem::badgeYn)
                .containsExactly(true, true, false);
        verify(mediaService).resolveViewUrl(IMAGE_KEY, MediaPurpose.ACHIEVEMENT_BADGE);
        verify(mediaService).resolveViewUrl(SECOND_IMAGE_KEY, MediaPurpose.ACHIEVEMENT_BADGE);
        verifyNoMoreInteractions(mediaService);
    }

    private Achievement achievement(
            Long id,
            String code,
            String badgeImageKey,
            boolean badgeYn
    ) {
        Achievement achievement = Achievement.builder()
                .code(code)
                .category(AchievementCategory.RARE)
                .name(code)
                .conditionDesc("condition")
                .progressType(AchievementProgressType.NONE)
                .targetCount(null)
                .conditionKey(null)
                .topazReward(10)
                .badgeYn(badgeYn)
                .limitedOutfitYn(false)
                .sortOrder(id.intValue())
                .active(true)
                .badgeImageKey(badgeImageKey)
                .build();
        ReflectionTestUtils.setField(achievement, "id", id);
        return achievement;
    }
}
