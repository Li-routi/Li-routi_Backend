package com.lirouti.domain.achievement.service.command;

import com.lirouti.domain.achievement.entity.MemberWaveRoutineStreak;
import com.lirouti.domain.achievement.repository.MemberWaveRoutineStreakRepository;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.exception.RoutineException;
import com.lirouti.domain.routine.exception.code.error.RoutineErrorCode;
import com.lirouti.domain.routine.repository.MemberRoutineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ACH-EG-013(파도) 진행도의 기준이 되는 "회원이 고른 루틴 하나"를 관리한다.
 *
 * <p>루틴을 바꾸면 지금까지 쌓은 연속 기록은 리셋된다 — 세부조건이 "사용자가 선택한
 * 개인 루틴 하나를 기준으로 한다"고 특정 루틴 하나에 고정하므로, 다른 루틴의 완료
 * 기록을 이어받을 근거가 없다.
 */
@Service
@RequiredArgsConstructor
public class WaveRoutineCommandService {

    private final MemberWaveRoutineStreakRepository memberWaveRoutineStreakRepository;
    private final MemberRoutineRepository memberRoutineRepository;

    @Transactional
    public void selectRoutine(Long memberId, Long memberRoutineId) {
        MemberRoutine routine = memberRoutineRepository
                .findByIdAndMemberIdAndActiveTrue(memberRoutineId, memberId)
                .orElseThrow(() -> new RoutineException(RoutineErrorCode.ROUTINE_NOT_FOUND));

        MemberWaveRoutineStreak streak = memberWaveRoutineStreakRepository
                .findByMemberIdForUpdate(memberId)
                .orElse(null);

        if (streak == null) {
            memberWaveRoutineStreakRepository.save(
                    MemberWaveRoutineStreak.builder()
                            .memberId(memberId)
                            .memberRoutineId(routine.getId())
                            .build());
            return;
        }
        if (streak.getMemberRoutineId().equals(routine.getId())) {
            return; // 이미 같은 루틴을 추적 중 - 리셋할 이유 없음
        }
        streak.changeTrackedRoutine(routine.getId());
    }
}
