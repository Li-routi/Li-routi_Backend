package com.lirouti.domain.achievement.service.command;

import com.lirouti.domain.achievement.entity.MemberRoutineStreak;
import com.lirouti.domain.achievement.event.AchievementProgressEvent;
import com.lirouti.domain.achievement.repository.MemberRoutineStreakRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 회원 전체 기준 루틴 연속기록(member_routine_streak)을 갱신하고, achievement 도메인에
 * ROUTINE_STREAK_DAYS 진행도 이벤트를 발행한다.
 *
 * <p>호출자(개인/그룹 루틴 인증 저장 트랜잭션)에 참여한다(MANDATORY) — 스트릭 갱신이
 * 인증 저장과 분리되면 인증은 롤백됐는데 스트릭만 남는 사고가 날 수 있다.
 *
 * <p>{@code sourceId} 는 반드시 호출자가 그때그때 저장한 인증 행의 id를 넘겨야 한다.
 * 회원 고정값(예: streak.getId())을 쓰면 achievement_progress_event_log 의 unique 제약
 * (source_type, source_id, condition_key, member_id) 에 첫 반영 이후 계속 걸려, 둘째
 * 날부터 스트릭 갱신 이벤트가 영영 무시된다.
 */
@Service
@RequiredArgsConstructor
public class MemberRoutineStreakCommandService {

    private static final String CONDITION_KEY_ROUTINE_STREAK_DAYS = "ROUTINE_STREAK_DAYS";

    private final MemberRoutineStreakRepository memberRoutineStreakRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCompletion(
            Long memberId,
            LocalDate completedDate,
            LocalDateTime occurredAt,
            String sourceType,
            Long sourceId
    ) {
        MemberRoutineStreak streak = memberRoutineStreakRepository.findByMemberIdForUpdate(memberId)
                .orElseGet(() -> memberRoutineStreakRepository.save(
                        MemberRoutineStreak.builder().memberId(memberId).build()));

        streak.recordCompletion(completedDate);

        if (eventPublisher == null) {
            return; // 단위 테스트가 이 서비스를 직접 생성한 경로
        }
        // amount 자리에 "이번에 늘어난 양"이 아니라 "지금 시점의 절대 스트릭 값"을 넣는다.
        // AchievementProgressService 의 STREAK_DAYS 케이스가 이 값으로 syncProgress(덮어쓰기)한다.
        eventPublisher.publishEvent(new AchievementProgressEvent(
                memberId,
                CONDITION_KEY_ROUTINE_STREAK_DAYS,
                streak.getCurrentStreak(),
                sourceType,
                sourceId,
                null,
                occurredAt
        ));
    }
}
