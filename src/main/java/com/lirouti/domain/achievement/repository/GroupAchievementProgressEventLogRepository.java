package com.lirouti.domain.achievement.repository;

import com.lirouti.domain.achievement.entity.GroupAchievementProgressEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupAchievementProgressEventLogRepository
        extends JpaRepository<GroupAchievementProgressEventLog, Long> {
    // 조회는 필요 없다 - GroupAchievementProgressService 가 saveAndFlush 시도 후
    // unique 제약 위반을 "이미 처리됨" 신호로 쓴다.
}
