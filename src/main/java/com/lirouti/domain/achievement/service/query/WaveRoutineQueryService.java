package com.lirouti.domain.achievement.service.query;

import com.lirouti.domain.achievement.dto.response.AchievementResDTO;
import com.lirouti.domain.achievement.entity.MemberWaveRoutineStreak;
import com.lirouti.domain.achievement.exception.AchievementException;
import com.lirouti.domain.achievement.exception.code.error.AchievementErrorCode;
import com.lirouti.domain.achievement.repository.MemberWaveRoutineStreakRepository;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.repository.MemberRoutineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WaveRoutineQueryService {

    private static final int WAVE_TARGET_STREAK = 10;

    private final MemberWaveRoutineStreakRepository memberWaveRoutineStreakRepository;
    private final MemberRoutineRepository memberRoutineRepository;

    @Transactional(readOnly = true)
    public AchievementResDTO.WaveRoutineStatus getStatus(Long memberId) {
        MemberWaveRoutineStreak streak = memberWaveRoutineStreakRepository
                .findByMemberId(memberId)
                .orElseThrow(() -> new AchievementException(AchievementErrorCode.WAVE_ROUTINE_NOT_SELECTED));

        MemberRoutine routine = memberRoutineRepository.findById(streak.getMemberRoutineId())
                .orElseThrow(() -> new AchievementException(AchievementErrorCode.NOT_FOUND));

        return AchievementResDTO.WaveRoutineStatus.builder()
                .memberRoutineId(routine.getId())
                .routineName(routine.getName())
                .currentStreak(streak.getCurrentStreak())
                .targetStreak(WAVE_TARGET_STREAK)
                .build();
    }
}
