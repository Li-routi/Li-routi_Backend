package com.lirouti.domain.achievement.service.query;

import com.lirouti.domain.achievement.converter.AchievementConverter;
import com.lirouti.domain.achievement.dto.response.AchievementResDTO;
import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.entity.MemberAchievement;
import com.lirouti.domain.achievement.entity.MemberAchievementCondition;
import com.lirouti.domain.achievement.enums.AchievementCategory;
import com.lirouti.domain.achievement.repository.AchievementRepository;
import com.lirouti.domain.achievement.repository.MemberAchievementConditionRepository;
import com.lirouti.domain.achievement.repository.MemberAchievementRepository;
import com.lirouti.domain.character.entity.CharacterUnlockCondition;
import com.lirouti.domain.character.repository.CharacterUnlockConditionRepository;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AchievementQueryService {

    private final AchievementRepository achievementRepository;
    private final MemberAchievementRepository memberAchievementRepository;
    private final MemberAchievementConditionRepository memberAchievementConditionRepository;
    private final CharacterUnlockConditionRepository characterUnlockConditionRepository;
    private final MediaService mediaService;

    @Transactional(readOnly = true)
    public AchievementResDTO.Achievements getMyAchievements(Long memberId) {
        List<Achievement> allAchievements = achievementRepository
                .findAllByActiveTrueOrderByCategoryAscSortOrderAsc();

        List<MemberAchievement> memberAchievements = memberAchievementRepository
                .findAllByMemberId(memberId);

        Map<Long, MemberAchievement> memberAchievementByAchievementId = memberAchievements.stream()
                .collect(Collectors.toMap(ma -> ma.getAchievement().getId(), Function.identity()));

        List<Long> memberAchievementIds = memberAchievements.stream()
                .map(MemberAchievement::getId)
                .toList();

        Map<Long, List<MemberAchievementCondition>> conditionProgressByMemberAchievementId =
                memberAchievementIds.isEmpty()
                        ? Map.of()
                        : memberAchievementIds.stream()
                          .collect(Collectors.toMap(
                                  id -> id,
                                  memberAchievementConditionRepository::findAllByMemberAchievementId
                          ));

        Map<Long, String> badgeImageUrlByAchievementId = buildBadgeImageUrlByAchievementId(allAchievements);

        return AchievementConverter.toAchievements(
                allAchievements,
                memberAchievementByAchievementId,
                conditionProgressByMemberAchievementId,
                badgeImageUrlByAchievementId
        );
    }

    /**
     * 응답의 "배지 이미지 자리"를 채운다. EPIC·UNIQUE 업적은 실제 배지
     * (achievement.badge_image_key)를, EGG 업적은 그 업적으로 열리는 캐릭터의 알 이미지
     * (avatar_character.egg_image_key)를 대신 채운다 — EGG는 achievement 쪽에
     * badge_image_key 자체가 없으므로 별도 조회가 필요하다.
     *
     * <p>이 자리는 어디까지나 "업적 목록 조회" 응답용이다. "달성" 탭(대표 업적 선택 후보,
     * {@code RepresentativeAchievementQueryService})은 badge_image_key가 있는 업적만
     * 걸러 쓰므로, EGG 업적은 애초에 그 화면에 나타나지 않는다 — 대표 업적으로 알을 설정할
     * 수는 없다는 뜻이라 여기서 별도 예외 처리가 필요 없다.
     */
    private Map<Long, String> buildBadgeImageUrlByAchievementId(List<Achievement> allAchievements) {
        Map<Long, String> result = new HashMap<>();

        allAchievements.stream()
                .filter(achievement -> achievement.getBadgeImageKey() != null)
                .forEach(achievement -> result.put(achievement.getId(), mediaService.resolveViewUrl(
                        achievement.getBadgeImageKey(), MediaPurpose.ACHIEVEMENT_BADGE)));

        allAchievements.stream()
                .filter(achievement -> achievement.getCategory() == AchievementCategory.EGG)
                .forEach(achievement -> {
                    List<CharacterUnlockCondition> conditions = characterUnlockConditionRepository
                            .findByAchievementCode(achievement.getCode());
                    if (conditions.isEmpty()) {
                        return; // 아직 캐릭터가 연결 안 된 EGG 업적 - 이미지 없이 둔다
                    }
                    String eggImageKey = conditions.get(0).getAvatarCharacter().getEggImageKey();
                    result.put(achievement.getId(), mediaService.resolveViewUrl(
                            eggImageKey, MediaPurpose.AVATAR_ASSET));  // ACHIEVEMENT_BADGE → AVATAR_ASSET
                });

        return result;
    }
}
