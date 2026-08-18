package com.lirouti.domain.achievement.service.command;

import com.lirouti.domain.achievement.entity.MemberWaveRoutineStreak;
import com.lirouti.domain.achievement.event.AchievementProgressEvent;
import com.lirouti.domain.achievement.repository.MemberWaveRoutineStreakRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ACH-EG-013(파도의 도전 → 파도) 전용 연속 기록 갱신. 회원이 고른 루틴이 완료됐을 때만
 * 호출된다 - "그 루틴이 맞는지" 판단은 호출자(RoutineVerificationCommandService)가 한다.
 */
@Service
@RequiredArgsConstructor
public class WaveRoutineStreakCommandService {

    private static final String CONDITION_KEY_WAVE_ROUTINE_STREAK_DAYS = "WAVE_ROUTINE_STREAK_DAYS";

    private final MemberWaveRoutineStreakRepository memberWaveRoutineStreakRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * @return 이번 완료가 실제로 "지금 추적 중인 루틴"의 완료였으면 true.
     *         추적 루틴을 아직 선택 안 했으면(행 없음) 갱신할 대상이 없어 false.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordCompletionIfTracked(
            Long memberId,
            Long completedMemberRoutineId,
            LocalDate completedDate,
            LocalDateTime occurredAt,
            String sourceType,
            Long sourceId
    ) {
        MemberWaveRoutineStreak streak = memberWaveRoutineStreakRepository
                .findByMemberIdForUpdate(memberId)
                .orElse(null);
        if (streak == null || !streak.getMemberRoutineId().equals(completedMemberRoutineId)) {
            return false;
        }

        streak.recordCompletion(completedDate);

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new AchievementProgressEvent(
                    memberId,
                    CONDITION_KEY_WAVE_ROUTINE_STREAK_DAYS,
                    streak.getCurrentStreak(),
                    sourceType,
                    sourceId,
                    null,
                    occurredAt
            ));
        }
        return true;
    }
}
