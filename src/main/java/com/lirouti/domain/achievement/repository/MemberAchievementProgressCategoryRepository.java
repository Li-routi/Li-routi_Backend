package com.lirouti.domain.achievement.repository;

import com.lirouti.domain.achievement.entity.MemberAchievementProgressCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberAchievementProgressCategoryRepository
        extends JpaRepository<MemberAchievementProgressCategory, Long> {

    /** CATEGORY_COVERAGE_COUNT(AC-008)용 - 이 업적에 대해 회원이 커버한 서로 다른 카테고리 총 개수. */
    long countByMemberAchievementId(Long memberAchievementId);
}
