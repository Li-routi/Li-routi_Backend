package com.lirouti.domain.achievement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 복합 조건 업적의 개별 조건. 지금은 ACH-SP-001(쿡쿡 100 + 좋아요 100) 하나뿐이지만,
 * 조건이 하나 더 느는 업적이 생겨도 이 테이블만 행을 더하면 되게 분리해 둔다.
 */
@Entity
@Getter
@Table(name = "achievement_condition")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AchievementCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "achievement_id", nullable = false)
    private Achievement achievement;

    /** 예: POKE_COUNT, LIKE_COUNT. 이벤트 처리기가 이 키로 어떤 값을 올릴지 찾는다. */
    @Column(name = "condition_key", nullable = false, length = 30)
    private String conditionKey;

    @Column(name = "target_count", nullable = false)
    private int targetCount;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder
    private AchievementCondition(Achievement achievement, String conditionKey,
                                 int targetCount, int sortOrder) {
        this.achievement = achievement;
        this.conditionKey = conditionKey;
        this.targetCount = targetCount;
        this.sortOrder = sortOrder;
    }
}
