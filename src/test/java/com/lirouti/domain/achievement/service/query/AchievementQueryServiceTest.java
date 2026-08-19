package com.lirouti.domain.achievement.service.query;

import com.lirouti.domain.achievement.dto.response.AchievementResDTO;
import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.entity.MemberAchievement;
import com.lirouti.domain.achievement.enums.AchievementCategory;
import com.lirouti.domain.achievement.enums.AchievementProgressType;
import com.lirouti.domain.achievement.enums.MemberAchievementStatus;
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

    /**
     * 히든 업적은 이름을 가린 채 자리만 남기지 않는다. 자리가 보이면 몇 개가 숨어 있는지와
     * 어느 카테고리인지가 드러나, 숨긴 뜻이 사라진다.
     */
    @Test
    @DisplayName("아직 달성하지 않은 히든 업적은 목록에도 요약에도 없다")
    void getMyAchievements_HidesUndiscoveredHiddenAchievement() {
        Achievement normal = achievement(21L, "ACH-001", null, true);
        Achievement hidden = hiddenAchievement(22L, "ACH-HIDDEN");
        when(achievementRepository.findAllByActiveTrueOrderByCategoryAscSortOrderAsc())
                .thenReturn(List.of(normal, hidden));
        when(memberAchievementRepository.findAllByMemberId(MEMBER_ID)).thenReturn(List.of());

        AchievementResDTO.Achievements result = achievementQueryService.getMyAchievements(MEMBER_ID);

        assertThat(result.categories())
                .flatExtracting(AchievementResDTO.CategoryGroup::achievements)
                .extracting(AchievementResDTO.AchievementItem::code)
                .as("숨긴 업적은 이름을 가린 자리조차 남기지 않는다")
                .containsExactly("ACH-001");
        assertThat(result.summary().totalCount())
                .as("분모에 남기면 목록에 없는 업적을 찾게 된다")
                .isEqualTo(1);
    }

    /** 받기 버튼이 목록 아래에 묻히면 사용자가 지금 할 수 있는 일을 못 찾는다. */
    @Test
    @DisplayName("받기 가능한 업적이 맨 위로 올라가고 나머지 순서는 그대로다")
    void getMyAchievements_PutsClaimableFirst() {
        Achievement first = achievement(21L, "ACH-001", null, true);
        Achievement second = achievement(22L, "ACH-002", null, true);
        Achievement third = achievement(23L, "ACH-003", null, true);
        when(achievementRepository.findAllByActiveTrueOrderByCategoryAscSortOrderAsc())
                .thenReturn(List.of(first, second, third));
        when(memberAchievementRepository.findAllByMemberId(MEMBER_ID)).thenReturn(List.of(
                memberAchievement(third, MemberAchievementStatus.ACHIEVED),
                memberAchievement(first, MemberAchievementStatus.CLAIMED)));

        AchievementResDTO.Achievements result = achievementQueryService.getMyAchievements(MEMBER_ID);

        assertThat(result.categories().get(0).achievements())
                .extracting(AchievementResDTO.AchievementItem::code)
                .as("받기 가능한 ACH-003 이 먼저, 나머지는 원래 순서")
                .containsExactly("ACH-003", "ACH-001", "ACH-002");
    }

    /** id 를 채우는 것은 조회 서비스가 진행도를 id 로 묶기 때문이다 — 비면 거기서 NPE 가 난다. */
    private MemberAchievement memberAchievement(Achievement achievement, MemberAchievementStatus status) {
        MemberAchievement memberAchievement = MemberAchievement.builder()
                .achievement(achievement)
                .build();
        ReflectionTestUtils.setField(memberAchievement, "id", achievement.getId());
        ReflectionTestUtils.setField(memberAchievement, "status", status);
        return memberAchievement;
    }

    private Achievement hiddenAchievement(Long id, String code) {
        Achievement achievement = Achievement.builder()
                .code(code)
                .category(AchievementCategory.RARE)
                .name(code)
                .conditionDesc("condition")
                .progressType(AchievementProgressType.NONE)
                .topazReward(10)
                .badgeYn(true)
                .limitedOutfitYn(false)
                .hiddenYn(true)
                .sortOrder(id.intValue())
                .active(true)
                .build();
        ReflectionTestUtils.setField(achievement, "id", id);
        return achievement;
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
