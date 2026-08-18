package com.lirouti.domain.character.service.evaluator;

import com.lirouti.domain.achievement.entity.MemberRoutineStreak;
import com.lirouti.domain.achievement.repository.MemberRoutineStreakRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 개인 루틴을 며칠 연속 했는가.
 *
 * <p><b>이미 있는 스트릭을 읽는다.</b> 개인 루틴 인증에서만 갱신되고, 하루에 하나라도 하면
 * 1 오르며, 결석 감지 배치가 끊기면 0 으로 되돌린다.
 *
 * <p><b>기획 원안과 다르다.</b> 원안은 "고른 루틴 하나를 그 예정일마다 10회 연속" 인데,
 * 개인 루틴은 <b>과거에 어떤 요일이 예정돼 있었는지 복원할 수 없어</b>(요일 행이 물리 삭제된다)
 * 루틴별 연속을 나중에 판정할 방법이 없다. 루틴별 스트릭 표를 새로 만들고 인증 시점마다
 * 갱신하면 되지만, 표와 훅이 늘고 고르는 화면도 없다. 그래서 "개인 루틴을 며칠 연속" 으로
 * 읽는다 — 원안보다 쉽지만 "꾸준히 해냈다" 는 뜻은 남는다.
 *
 * <p><b>{@code STREAK_DAYS} 와 다르다.</b> 그쪽은 활동일(개인·그룹·챌린지 합산) 연속이라,
 * 그룹·챌린지만 하는 사람은 그것만 오르고 이 값은 오르지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RoutineStreakEvaluator implements UnlockConditionEvaluator {

    private final MemberRoutineStreakRepository memberRoutineStreakRepository;

    @Override
    public String conditionKey() {
        return "ROUTINE_STREAK";
    }

    @Override
    public long count(Long memberId, String conditionParam) {
        return memberRoutineStreakRepository.findByMemberId(memberId)
                .map(MemberRoutineStreak::getCurrentStreak)
                .orElse(0);
    }
}
