package com.lirouti.domain.achievement.service;

import com.lirouti.domain.achievement.entity.GroupAchievementProgressEventLog;
import com.lirouti.domain.achievement.repository.GroupAchievementProgressEventLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GroupAchievementProgressEvent} 하나를 특정 업적에 대해 "처음 반영하는 것"으로
 * 확정 짓는다. {@code AchievementProgressEventLogService} 와 같은 이유로 별도 빈 +
 * {@code REQUIRES_NEW}.
 */
@Service
@RequiredArgsConstructor
public class GroupAchievementProgressEventLogService {

    private final GroupAchievementProgressEventLogRepository groupAchievementProgressEventLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryMarkProcessed(Long groupId, Long achievementId, String sourceType, Long sourceId) {
        int affected = groupAchievementProgressEventLogRepository.insertIfAbsent(groupId, achievementId, sourceType, sourceId);
        return affected == 1;
    }
}
