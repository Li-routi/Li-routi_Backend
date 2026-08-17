package com.lirouti.domain.achievement.service;

import com.lirouti.domain.achievement.entity.AchievementProgressEventLog;
import com.lirouti.domain.achievement.repository.AchievementProgressEventLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code AchievementProgressEvent} 하나를 "처음 본 것"으로 확정 짓는 역할만 한다.
 *
 * <p>별도 빈으로 분리한 이유: {@code REQUIRES_NEW} 로 독립 트랜잭션을 걸어야 unique
 * 제약 위반이 나도 그 실패가 진행도 갱신 트랜잭션 전체를 rollback-only 로 만들지
 * 않는다. 같은 클래스 안의 self-invocation 으로는 Spring AOP 프록시가 적용되지 않아
 * propagation 설정이 무시되므로, 반드시 다른 빈을 거쳐 호출해야 한다.
 */
@Service
@RequiredArgsConstructor
public class AchievementProgressEventLogService {

    private final AchievementProgressEventLogRepository achievementProgressEventLogRepository;

    /**
     * @return 처음 처리하는 이벤트면 true, 이미 처리된 적 있어(unique 제약 위반) 이번
     *         호출은 건너뛰어야 하면 false.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryMarkProcessed(Long memberId, String conditionKey, String sourceType, Long sourceId) {
        int affected = achievementProgressEventLogRepository
                .insertIfAbsent(memberId, conditionKey, sourceType, sourceId);
        return affected == 1;
    }

    /**
     * 모든 업적 처리 작업이 실패한 경우, 이후 이벤트 재발행 시 다시 시도할 수 있도록
     * 기록된 이벤트 로그를 삭제(원복)한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void unmarkProcessed(Long memberId, String conditionKey, String sourceType, Long sourceId) {
        achievementProgressEventLogRepository.deleteByMemberIdAndConditionKeyAndSourceTypeAndSourceId(
                memberId, conditionKey, sourceType, sourceId
        );
    }
}
