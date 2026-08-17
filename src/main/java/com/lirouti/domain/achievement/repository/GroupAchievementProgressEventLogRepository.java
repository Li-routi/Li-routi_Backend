package com.lirouti.domain.achievement.repository;

import com.lirouti.domain.achievement.entity.GroupAchievementProgressEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupAchievementProgressEventLogRepository
        extends JpaRepository<GroupAchievementProgressEventLog, Long> {
    // 조회는 필요 없다 - GroupAchievementProgressService 가 saveAndFlush 시도 후
    // unique 제약 위반을 "이미 처리됨" 신호로 쓴다.

    // useAffectedRows=true 필요: MySQL JDBC 기본값(found rows)이 아니라
    // 실제 변경된 행 수를 반환하게 함 → 신규 insert=1, 중복(no-op update)=0
    @Modifying
    @Query(value = """
        INSERT INTO group_achievement_progress_event_log
            (group_id, achievement_id, source_type, source_id, created_at)
        VALUES
            (:groupId, :achievementId, :sourceType, :sourceId, NOW())
        ON DUPLICATE KEY UPDATE
            id = id
        """, nativeQuery = true)
    int insertIfAbsent(@Param("groupId") Long groupId,
                       @Param("achievementId") Long achievementId,
                       @Param("sourceType") String sourceType,
                       @Param("sourceId") Long sourceId);
}
