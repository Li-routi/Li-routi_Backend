package com.lirouti.domain.achievement.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class AchievementReqDTO {
    private AchievementReqDTO() {
    }

    public record SelectWaveRoutine(
            @NotNull(message = "루틴 ID는 필수입니다.")
            @Positive(message = "루틴 ID는 양수여야 합니다.")
            Long memberRoutineId
    ) {
    }

    public record SelectRepresentativeAchievement(
            @NotNull(message = "업적 ID는 필수입니다.")
            @Positive(message = "업적 ID는 양수여야 합니다.")
            Long achievementId
    ) {
    }
}
