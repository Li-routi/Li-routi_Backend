package com.lirouti.domain.achievement.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * CATEGORY_COVERAGE_COUNT(예: 루틴 탐험가) 업적이 "서로 다른 카테고리를 커버했는지"를 세는
 * 근거 테이블.
 *
 * <p>회원이 이 업적에 대해 커버한 루틴 카테고리를 카테고리당 한 행만 남긴다. unique 제약
 * ({@code member_achievement_id, routine_category_id}) 덕분에 같은 카테고리 루틴을 여러 번
 * 완료해도 두 번째부터는 insert가 실패하고, {@code MemberAchievementProgressCategoryService}
 * 는 이 실패를 "이미 이 카테고리는 커버했다"는 신호로 써서 진행도를 중복 증가시키지 않는다.
 *
 * <p>{@link MemberAchievementProgressDay} 와 동일한 설계 원칙을 따른다 - 감사 컬럼 없이
 * 최소 컬럼만 두는 append-only 마킹 로그다. {@code created_at} 은 DB의
 * {@code DEFAULT CURRENT_TIMESTAMP} 에만 맡기고 엔티티는 매핑하지 않는다.
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
