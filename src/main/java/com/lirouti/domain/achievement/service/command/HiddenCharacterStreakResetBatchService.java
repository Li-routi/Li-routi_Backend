package com.lirouti.domain.achievement.service.command;

import com.lirouti.domain.achievement.entity.MemberMorningRoutineStreak;
import com.lirouti.domain.achievement.entity.MemberWaveRoutineStreak;
import com.lirouti.domain.achievement.repository.MemberMorningRoutineStreakRepository;
import com.lirouti.domain.achievement.repository.MemberWaveRoutineStreakRepository;
import com.lirouti.domain.routine.repository.MemberRoutineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * 노아(ACH-EG-003)·파도(ACH-EG-013) 연속 기록을, 예정된 날 완료하지 못하면 0으로
 * 초기화한다. 개인 루틴은 그날 예정 여부를 담는 행이 따로 없어(요일 반복 패턴만 있음)
 * 그룹 루틴 마감 배치처럼 매일 한 번 "어제"를 확정하는 배치가 필요하다.
 *
 * <p>KST 자정 직후에 "어제"를 기준으로 판정한다 - 자정이 지나야 어제 하루가
 * 완전히 끝났다고 볼 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HiddenCharacterStreakResetBatchService {

    private final MemberRoutineRepository memberRoutineRepository;
    private final MemberMorningRoutineStreakRepository memberMorningRoutineStreakRepository;
    private final MemberWaveRoutineStreakRepository memberWaveRoutineStreakRepository;

    @Transactional
    public void resetMorningRoutineStreaksForMissedDay(LocalDate yesterday) {
        DayOfWeek day = yesterday.getDayOfWeek();
        List<Long> memberIds = memberRoutineRepository.findMemberIdsWithActiveMorningRoutineScheduledOn(
                MorningRoutineStreakCommandService.MORNING_ROUTINE_END_TIME_CUTOFF, day);

        int resetCount = 0;
        for (Long memberId : memberIds) {
            MemberMorningRoutineStreak streak = memberMorningRoutineStreakRepository
                    .findByMemberIdForUpdate(memberId)
                    .orElse(null);
            if (streak == null) {
                continue; // 한 번도 완료한 적 없으면 리셋할 것도 없다
            }
            if (!yesterday.equals(streak.getLastStreakCompletedDate())) {
                streak.resetCurrentStreak();
                resetCount++;
            }
        }
        if (resetCount > 0) {
            log.info("기상 루틴 연속 기록을 초기화했습니다. date={}, resetCount={}", yesterday, resetCount);
        }
    }

    @Transactional
    public void resetWaveRoutineStreaksForMissedDay(LocalDate yesterday) {
        DayOfWeek day = yesterday.getDayOfWeek();
        List<Long> memberIds = memberWaveRoutineStreakRepository.findAll().stream()
                .map(MemberWaveRoutineStreak::getMemberId)
                .toList();

        int resetCount = 0;
        for (Long memberId : memberIds) {
            MemberWaveRoutineStreak streak = memberWaveRoutineStreakRepository
                    .findByMemberIdForUpdate(memberId)
                    .orElse(null);
            if (streak == null) {
                continue;
            }
            boolean wasScheduled = memberRoutineRepository
                    .existsActiveScheduledOn(streak.getMemberRoutineId(), day);
            if (!wasScheduled) {
                continue; // 예정되지 않은 날은 연속 기록에 영향을 주지 않는다
            }
            if (!yesterday.equals(streak.getLastStreakCompletedDate())) {
                streak.resetCurrentStreak();
                resetCount++;
            }
        }
        if (resetCount > 0) {
            log.info("파도 루틴 연속 기록을 초기화했습니다. date={}, resetCount={}", yesterday, resetCount);
        }
    }
}
