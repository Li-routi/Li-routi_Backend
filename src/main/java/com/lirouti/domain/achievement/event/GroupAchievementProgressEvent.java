package com.lirouti.domain.achievement.event;

public record GroupAchievementProgressEvent(
        Long groupId,
        String achievementCode,   // condition_key가 없으니 코드로 직접 지정
        int incrementAmount,
        String sourceType,
        Long sourceId
) {
}
