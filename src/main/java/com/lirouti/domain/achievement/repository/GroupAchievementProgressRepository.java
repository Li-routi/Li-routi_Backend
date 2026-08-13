package com.lirouti.domain.achievement.repository;

import com.lirouti.domain.achievement.entity.GroupAchievementProgress;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GroupAchievementProgressRepository
        extends JpaRepository<GroupAchievementProgress, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from GroupAchievementProgress p
            where p.groupId = :groupId and p.achievement.id = :achievementId
            """)
    Optional<GroupAchievementProgress> findForUpdate(
            @Param("groupId") Long groupId, @Param("achievementId") Long achievementId);

    // 동시성 대비용: 없으면 삽입, 있으면 no-op(id=id). 락 없이 행의 존재만 보장한다.
    // 이후 findForUpdate로 다시 조회해 잠금을 건다.
    @Modifying
    @Query(value = """
        INSERT INTO group_achievement_progress
            (group_id, achievement_id, current_progress, created_at)
        VALUES
            (:groupId, :achievementId, 0, NOW())
        ON DUPLICATE KEY UPDATE
            id = id
        """, nativeQuery = true)
    void insertIfAbsent(@Param("groupId") Long groupId, @Param("achievementId") Long achievementId);
}
