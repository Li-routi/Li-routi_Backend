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
            Summary summary,
            List<CategoryGroup> categories
    ) {
    }

    /**
     * 화면 상단 요약 카드. "획득 8/36", "진행 중 12", "스페셜 1/4"에 대응한다.
     *
     * <p>{@code inProgressCount}는 아직 미달성이면서 진행도가 0보다 큰 업적 수다 —
     * 시작도 안 한 업적까지 세면 "진행 중"이라는 말과 어긋난다.
     */
    @Builder
    public record Summary(
            int acquiredCount,
            int totalCount,
            int inProgressCount,
            int specialAcquiredCount,
            int specialTotalCount
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
            String badgeImageUrl,
            String conditionDesc,
            MemberAchievementStatus status,
            Progress progress,
            List<ConditionProgress> conditionProgresses,
            int topazReward,
            boolean badgeYn,
            boolean limitedOutfitYn,
            boolean hiddenYn,
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

    /** 업적 보상 수령 결과. */
    @Builder
    public record Claim(
            Long achievementId,
            int freeBalanceAfter,
            boolean rewardApplied
    ) {
    }

    /** 관리자 뱃지 이미지 등록·교체 결과. object key는 외부에 노출하지 않는다. */
    @Builder
    public record AdminBadgeImage(
            Long achievementId,
            String code,
            String badgeImageUrl
    ) {
    }

    @Builder
    public record WaveRoutineStatus(
            Long memberRoutineId,
            String routineName,
            int currentStreak,
            int targetStreak
    ) {
    }
}
