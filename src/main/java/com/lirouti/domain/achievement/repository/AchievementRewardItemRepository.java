package com.lirouti.domain.achievement.repository;

import com.lirouti.domain.achievement.entity.AchievementRewardItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AchievementRewardItemRepository extends JpaRepository<AchievementRewardItem, Long> {

    @Query("""
            select r from AchievementRewardItem r
            where r.achievement.code = :achievementCode
            and r.itemCategory = 'OUTFIT'
            """)
    List<AchievementRewardItem> findOutfitItemsByAchievementCode(@Param("achievementCode") String achievementCode);
}
