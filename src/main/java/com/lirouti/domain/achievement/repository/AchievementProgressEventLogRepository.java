package com.lirouti.domain.achievement.repository;

import com.lirouti.domain.achievement.entity.AchievementProgressEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AchievementProgressEventLogRepository
        extends JpaRepository<AchievementProgressEventLog, Long> {

    // useAffectedRows=true 필요 (datasource URL 확인)
    // 신규 insert → 1, 이미 존재(no-op update) → 0
    @Modifying
    @Query(value = """
        INSERT INTO achievement_progress_event_log
            (member_id, condition_key, source_type, source_id, created_at)
        VALUES
            (:memberId, :conditionKey, :sourceType, :sourceId, NOW())
        ON DUPLICATE KEY UPDATE
            id = id
        """, nativeQuery = true)
    int insertIfAbsent(@Param("memberId") Long memberId,
                       @Param("conditionKey") String conditionKey,
                       @Param("sourceType") String sourceType,
                       @Param("sourceId") Long sourceId);

    void deleteByMemberIdAndConditionKeyAndSourceTypeAndSourceId(
            Long memberId,
            String conditionKey,
            String sourceType,
            Long sourceId
    );
}
