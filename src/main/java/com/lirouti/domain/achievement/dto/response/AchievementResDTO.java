package com.lirouti.domain.achievement.dto.response;

import com.lirouti.domain.achievement.enums.AchievementCategory;
import com.lirouti.domain.achievement.enums.MemberAchievementStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

public final class AchievementResDTO {

    private AchievementResDTO() {
    }

    @Builder
    public record Achievements(
            List<CategoryGroup> categories
    ) {
    }

    @Builder
    public record CategoryGroup(
            AchievementCategory category,
            List<AchievementItem> achievements
    ) {
    }

    @Builder
    public record AchievementItem(
            Long achievementId,
            String code,
            String name,
            String conditionDesc,
            MemberAchievementStatus status,
            Progress progress,
            List<ConditionProgress> conditionProgresses,
            int topazReward,
            boolean badgeYn,
            boolean limitedOutfitYn,
            LocalDateTime achievedAt,
            LocalDateTime claimedAt
    ) {
    }

    /** 단일 조건 업적의 진행도. COMPOSITE 는 null — conditionProgresses 를 본다. */
    @Builder
    public record Progress(
            int current,
            int target
    ) {
    }

    @Builder
    public record ConditionProgress(
            String conditionKey,
            int current,
            int target
    ) {
    }
}
