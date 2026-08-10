package com.lirouti.domain.achievement.enums;

public enum MemberAchievementStatus {
    /** 조건 미충족. */
    IN_PROGRESS,
    /** 조건 충족, 보상 미수령. */
    ACHIEVED,
    /** 보상 수령 완료. */
    CLAIMED
}
