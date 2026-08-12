package com.lirouti.domain.achievement.repository;

import com.lirouti.domain.achievement.entity.GroupAchievementProgress;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GroupAchievementProgressRepository extends JpaRepository<GroupAchievementProgress, Long> {

    /**
     * 이벤트 처리기가 진행도를 올릴 때 잠근다. {@code MemberAchievementRepository.findForUpdate}
     * 와 같은 이유 — 같은 그룹에 짧은 시간 안에 여러 인증이 들어와도 순차 처리되게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from GroupAchievementProgress p
            where p.groupId = :groupId and p.achievement.id = :achievementId
            """)
    Optional<GroupAchievementProgress> findForUpdate(
            @Param("groupId") Long groupId, @Param("achievementId") Long achievementId);
}
