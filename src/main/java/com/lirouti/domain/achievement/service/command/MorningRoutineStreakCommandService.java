package com.lirouti.domain.achievement.service.command;

import com.lirouti.domain.achievement.entity.MemberMorningRoutineStreak;
import com.lirouti.domain.achievement.event.AchievementProgressEvent;
import com.lirouti.domain.achievement.repository.MemberMorningRoutineStreakRepository;
import com.lirouti.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * ACH-EG-003(일찍 일어난 새 → 노아) 전용 연속 기록. "기상 루틴"(마감이 이 시각 이전인
 * 루틴)만 필터링한다 - member_routine_streak(전체 루틴 통합)과 완전히 별개다.
 *
 * <p>호출자(루틴 인증 저장 트랜잭션)에 MANDATORY로 참여한다 - 스트릭 갱신이 인증 저장과
 * 분리되면 인증은 롤백됐는데 스트릭만 남는 사고가 날 수 있다(MemberRoutineStreakCommandService와
 * 같은 이유).
 */
@Service
@RequiredArgsConstructor
public class MorningRoutineStreakCommandService {

    /** "기상·아침 성격의 루틴"의 판정 기준 - 마감이 이 시각 이전이면 기상 루틴으로 본다. */
    public static final LocalTime MORNING_ROUTINE_END_TIME_CUTOFF = LocalTime.of(10, 0);

    private static final String CONDITION_KEY_MORNING_ROUTINE_STREAK_DAYS = "MORNING_ROUTINE_STREAK_DAYS";

    private final MemberMorningRoutineStreakRepository memberMorningRoutineStreakRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCompletion(
            Long memberId,
            LocalDate completedDate,
            LocalDateTime occurredAt,
            String sourceType,
            Long sourceId
    ) {
        MemberMorningRoutineStreak streak = getOrCreateForUpdate(memberId);
        streak.recordCompletion(completedDate);

        if (eventPublisher == null) {
            return;
        }
        // STREAK_DAYS는 amount를 "지금 시점의 절대 스트릭 값"으로 해석한다
        // (AchievementProgressService.syncProgress 참고) - MemberRoutineStreakCommandService와 동일한 규약.
        eventPublisher.publishEvent(new AchievementProgressEvent(
                memberId,
                CONDITION_KEY_MORNING_ROUTINE_STREAK_DAYS,
                streak.getCurrentStreak(),
                sourceType,
                sourceId,
                null,
                occurredAt
        ));
    }

    private MemberMorningRoutineStreak getOrCreateForUpdate(Long memberId) {
        memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found. memberId=" + memberId));

        if (memberMorningRoutineStreakRepository.findByMemberIdForUpdate(memberId).isEmpty()) {
            memberMorningRoutineStreakRepository.saveAndFlush(
                    MemberMorningRoutineStreak.builder().memberId(memberId).build());
        }

        return memberMorningRoutineStreakRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new IllegalStateException(
                        "MemberMorningRoutineStreak was not created. memberId=" + memberId));
    }
}
