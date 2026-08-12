package com.lirouti.domain.achievement.repository;

import com.lirouti.domain.achievement.entity.AchievementProgressEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AchievementProgressEventLogRepository
        extends JpaRepository<AchievementProgressEventLog, Long> {
    // 조회는 필요 없다 — AchievementProgressService 가 saveAndFlush 시도 후
    // unique 제약 위반(DataIntegrityViolationException)을 "이미 처리됨" 신호로 쓴다.
}
