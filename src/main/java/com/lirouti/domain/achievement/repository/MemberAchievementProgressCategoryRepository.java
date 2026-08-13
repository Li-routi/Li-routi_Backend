package com.lirouti.domain.achievement.repository;

import com.lirouti.domain.achievement.entity.MemberAchievementProgressCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberAchievementProgressCategoryRepository
        extends JpaRepository<MemberAchievementProgressCategory, Long> {

    /** CATEGORY_COVERAGE_COUNT(AC-008)용 - 이 업적에 대해 회원이 커버한 서로 다른 카테고리 총 개수. */
    long countByMemberAchievementId(Long memberAchievementId);

    /**
     * @return inserted row count (1 = new category, 0 = already covered category)
     */
    @Modifying(flushAutomatically = true)
    @Query(
            value = "INSERT IGNORE INTO member_achievement_progress_category "
                    + "(member_achievement_id, routine_category_id) "
                    + "VALUES (:memberAchievementId, :routineCategoryId)",
            nativeQuery = true
    )
    int insertIgnore(
            @Param("memberAchievementId") Long memberAchievementId,
            @Param("routineCategoryId") Long routineCategoryId
    );
}
