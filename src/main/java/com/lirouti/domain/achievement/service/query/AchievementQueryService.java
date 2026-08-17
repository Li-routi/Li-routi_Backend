package com.lirouti.domain.achievement.service.query;

import com.lirouti.domain.achievement.converter.AchievementConverter;
import com.lirouti.domain.achievement.dto.response.AchievementResDTO;
import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.entity.MemberAchievement;
import com.lirouti.domain.achievement.entity.MemberAchievementCondition;
import com.lirouti.domain.achievement.repository.AchievementRepository;
import com.lirouti.domain.achievement.repository.MemberAchievementConditionRepository;
import com.lirouti.domain.achievement.repository.MemberAchievementRepository;
import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        Map<Long, String> badgeImageUrlByAchievementId = allAchievements.stream()
                .filter(achievement -> achievement.getBadgeImageKey() != null)
                .collect(Collectors.toMap(
                        Achievement::getId,
                        achievement -> mediaService.resolveViewUrl(
                                achievement.getBadgeImageKey(),
                                MediaPurpose.ACHIEVEMENT_BADGE
                        )
                ));

        return AchievementConverter.toAchievements(
                allAchievements,
                memberAchievementByAchievementId,
                conditionProgressByMemberAchievementId,
                badgeImageUrlByAchievementId
        );
    }
}
