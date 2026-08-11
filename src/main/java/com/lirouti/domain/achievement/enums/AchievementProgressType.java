package com.lirouti.domain.achievement.enums;

import com.lirouti.domain.achievement.entity.MemberAchievement;

/**
 * 진행도 계산 방식.
 *
 * <p>단일 조건 업적은 {@link MemberAchievement#getCurrentProgress()}
 * 하나로 충분하지만, {@code COMPOSITE} 는 조건이 여러 개라 각각 따로 세야 한다
 * ({@link com.lirouti.domain.achievement.entity.MemberAchievementCondition}).
 */
public enum AchievementProgressType {
    /** 진행률 없음. 이벤트 발생 즉시 달성 (예: 첫 좋아요). */
    NONE,
    /** 단순 누적 횟수. */
    CUMULATIVE_COUNT,
    /** 서로 다른 방 참여 수. */
    DISTINCT_ROOM_COUNT,
    /** 여러 조건을 모두 만족해야 하는 복합 조건. */
    COMPOSITE
}
