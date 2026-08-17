package com.lirouti.domain.achievement.converter;

import com.lirouti.domain.achievement.dto.response.AchievementResDTO;
import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.entity.AchievementCondition;
import com.lirouti.domain.achievement.entity.MemberAchievement;
import com.lirouti.domain.achievement.entity.MemberAchievementCondition;
import com.lirouti.domain.achievement.enums.AchievementCategory;
import com.lirouti.domain.achievement.enums.AchievementProgressType;
import com.lirouti.domain.achievement.enums.MemberAchievementStatus;

import java.util.*;
import java.util.stream.Collectors;

public final class AchievementConverter {

    private AchievementConverter() {
    }

    /**
     * 전체 업적 정의와 회원 진행도, 업적별 이미지 URL을 회원용 응답으로 조합한다.
     * 회원이 아직 진행 중이지 않은 업적도 목록에 포함하며, 이미지 key가 없는 업적은
     * URL map에 포함하지 않아 응답에서 {@code null}로 유지한다.
     */
    public static AchievementResDTO.Achievements toAchievements(
            List<Achievement> allAchievements,
            Map<Long, MemberAchievement> memberAchievementByAchievementId,
            Map<Long, List<MemberAchievementCondition>> conditionProgressByMemberAchievementId,
            Map<Long, String> badgeImageUrlByAchievementId
    ) {
        Map<com.lirouti.domain.achievement.enums.AchievementCategory, List<AchievementResDTO.AchievementItem>> grouped =
                allAchievements.stream()
                        .collect(Collectors.groupingBy(
                                Achievement::getCategory,
                                LinkedHashMap::new,
                                Collectors.mapping(
                                        a -> toItem(a, memberAchievementByAchievementId.get(a.getId()),
                                                conditionProgressByMemberAchievementId,
                                                badgeImageUrlByAchievementId),
                                        Collectors.toList()
                                )
                        ));

        List<AchievementResDTO.CategoryGroup> categories = grouped.entrySet().stream()
                .map(e -> AchievementResDTO.CategoryGroup.builder()
                        .category(e.getKey())
                        .achievements(e.getValue())
                        .build())
                .toList();

        AchievementResDTO.Summary summary =
                toSummary(allAchievements, memberAchievementByAchievementId);

        return AchievementResDTO.Achievements.builder()
                .summary(summary)
                .categories(categories)
                .build();
    }

    /**
     * 상단 요약 카드. 획득/전체, 진행 중, 스페셜(UNIQUE 카테고리) 획득/전체를 센다.
     *
     * <p>"진행 중"은 아직 미달성이면서 진행도가 0보다 큰 업적만 센다. COMPOSITE 업적은
     * {@code currentProgress} 컬럼을 쓰지 않으므로 member_achievement 행이 있다는 것
     * 자체를 "손을 댄 적 있다"로 보고 진행 중에 포함한다.
     */
    private static AchievementResDTO.Summary toSummary(
            List<Achievement> allAchievements,
            Map<Long, MemberAchievement> memberAchievementByAchievementId
    ) {
        int acquired = 0;
        int inProgress = 0;
        int specialTotal = 0;
        int specialAcquired = 0;

        for (Achievement achievement : allAchievements) {
            MemberAchievement memberAchievement = memberAchievementByAchievementId.get(achievement.getId());
            boolean isSpecial = achievement.getCategory() == AchievementCategory.UNIQUE;
            if (isSpecial) {
                specialTotal++;
            }

            if (memberAchievement == null) {
                continue;
            }
            MemberAchievementStatus status = memberAchievement.getStatus();
            if (status == MemberAchievementStatus.ACHIEVED || status == MemberAchievementStatus.CLAIMED) {
                acquired++;
                if (isSpecial) {
                    specialAcquired++;
                }
            } else if (achievement.getProgressType() == AchievementProgressType.COMPOSITE
                    || memberAchievement.getCurrentProgress() > 0) {
                inProgress++;
            }
        }

        return AchievementResDTO.Summary.builder()
                .acquiredCount(acquired)
                .totalCount(allAchievements.size())
                .inProgressCount(inProgress)
                .specialAcquiredCount(specialAcquired)
                .specialTotalCount(specialTotal)
                .build();
    }

    private static AchievementResDTO.AchievementItem toItem(
            Achievement achievement,
            MemberAchievement memberAchievement,
            Map<Long, List<MemberAchievementCondition>> conditionProgressByMemberAchievementId,
            Map<Long, String> badgeImageUrlByAchievementId
    ) {
        MemberAchievementStatus status = memberAchievement != null
                ? memberAchievement.getStatus()
                : MemberAchievementStatus.IN_PROGRESS;

        AchievementResDTO.Progress progress = null;
        List<AchievementResDTO.ConditionProgress> conditionProgresses = List.of();

        if (achievement.getProgressType() == AchievementProgressType.COMPOSITE) {
            List<MemberAchievementCondition> progresses = memberAchievement != null
                    ? conditionProgressByMemberAchievementId.getOrDefault(memberAchievement.getId(), List.of())
                    : List.of();
            Map<String, Integer> currentByKey = progresses.stream()
                    .collect(Collectors.toMap(MemberAchievementCondition::getConditionKey,
                            MemberAchievementCondition::getCurrentValue));

            conditionProgresses = achievement.getConditions().stream()
                    .sorted(Comparator.comparingInt(AchievementCondition::getSortOrder))
                    .map(c -> AchievementResDTO.ConditionProgress.builder()
                            .conditionKey(c.getConditionKey())
                            .current(currentByKey.getOrDefault(c.getConditionKey(), 0))
                            .target(c.getTargetCount())
                            .build())
                    .toList();
        } else if (achievement.getProgressType() != AchievementProgressType.NONE) {
            int current = memberAchievement != null ? memberAchievement.getCurrentProgress() : 0;
            progress = AchievementResDTO.Progress.builder()
                    .current(current)
                    .target(achievement.getTargetCount())
                    .build();
        }

        return AchievementResDTO.AchievementItem.builder()
                .achievementId(achievement.getId())
                .code(achievement.getCode())
                .name(achievement.getName())
                .badgeImageUrl(badgeImageUrlByAchievementId.get(achievement.getId()))
                .conditionDesc(achievement.getConditionDesc())
                .status(status)
                .progress(progress)
                .conditionProgresses(conditionProgresses)
                .topazReward(achievement.getTopazReward())
                .badgeYn(achievement.isBadgeYn())
                .limitedOutfitYn(achievement.isLimitedOutfitYn())
                .achievedAt(memberAchievement != null ? memberAchievement.getAchievedAt() : null)
                .claimedAt(memberAchievement != null ? memberAchievement.getClaimedAt() : null)
                .build();
    }
}
