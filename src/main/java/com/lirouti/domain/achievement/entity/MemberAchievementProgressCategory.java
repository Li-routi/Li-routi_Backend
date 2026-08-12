package com.lirouti.domain.achievement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * CATEGORY_COVERAGE_COUNT(루틴 탐험가) 업적이 "서로 다른 카테고리"를 세는 근거 테이블.
 *
 * <p>{@link MemberAchievementProgressDay} 가 날짜 축으로 dedup 하는 것과 정확히 같은 패턴을
 * 카테고리 축에 적용한다 — unique 제약(member_achievement_id, routine_category_id) 덕분에
 * 같은 카테고리 루틴을 여러 번 완료해도 두 번째부터는 insert가 실패하고,
 * {@code MemberAchievementProgressCategoryService} 가 이 실패를 "이 카테고리는 이미 커버됨"
 * 신호로 쓴다.
 */
@Entity
@Getter
@Table(
        name = "member_achievement_progress_category",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_achievement_progress_category",
                columnNames = {"member_achievement_id", "routine_category_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAchievementProgressCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_achievement_id", nullable = false)
    private MemberAchievement memberAchievement;

    @Column(name = "routine_category_id", nullable = false)
    private Long routineCategoryId;

    @Builder
    private MemberAchievementProgressCategory(MemberAchievement memberAchievement, Long routineCategoryId) {
        this.memberAchievement = memberAchievement;
        this.routineCategoryId = routineCategoryId;
    }
}
