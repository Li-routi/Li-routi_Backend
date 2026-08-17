package com.lirouti.domain.achievement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 복합 조건 업적의 회원별 조건별 진행도. */
@Entity
@Getter
@Table(
        name = "member_achievement_condition",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_achievement_condition",
                columnNames = {"member_achievement_id", "condition_key"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAchievementCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_achievement_id", nullable = false)
    private MemberAchievement memberAchievement;

    @Column(name = "condition_key", nullable = false, length = 30)
    private String conditionKey;

    @Column(name = "current_value", nullable = false)
    private int currentValue;

    @Builder
    private MemberAchievementCondition(MemberAchievement memberAchievement,
                                       String conditionKey, int currentValue) {
        this.memberAchievement = memberAchievement;
        this.conditionKey = conditionKey;
        this.currentValue = currentValue;
    }

    public void increase(int amount, int targetCount) {
        this.currentValue = Math.min(this.currentValue + amount, targetCount);
    }
}
