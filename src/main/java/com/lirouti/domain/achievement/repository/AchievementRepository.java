package com.lirouti.domain.achievement.repository;

import com.lirouti.domain.achievement.entity.Achievement;
import com.lirouti.domain.achievement.enums.AchievementCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AchievementRepository extends JpaRepository<Achievement, Long> {

    Optional<Achievement> findByCode(String code);
    List<Achievement> findAllByActiveTrueOrderByCategoryAscSortOrderAsc();
    List<Achievement> findAllByActiveTrueAndCategoryOrderBySortOrderAsc(AchievementCategory category);
}
