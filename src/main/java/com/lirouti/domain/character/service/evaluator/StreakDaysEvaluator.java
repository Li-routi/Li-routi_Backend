package com.lirouti.domain.character.service.evaluator;

import com.lirouti.domain.activity.repository.MemberActivityDayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 활동일 <b>연속</b>.
 *
 * <p><b>{@code ACTIVE_DAYS} 와 다르다.</b> 앞은 하루라도 비면 0 으로 돌아가고 뒤는 줄지
 * 않는다 — 같은 100 이라도 난이도가 전혀 다르다.
 *
 * <p>가장 최근 활동일부터 하루씩 거슬러 이어지는 만큼 센다. 판정은 활동을 기록한 직후에
 * 돌므로 그 최근일이 곧 오늘이다.
 */
@Component
@RequiredArgsConstructor
public class StreakDaysEvaluator implements UnlockConditionEvaluator {

    private final MemberActivityDayRepository memberActivityDayRepository;

    @Override
    public String conditionKey() {
        return "STREAK_DAYS";
    }

    @Override
    public long count(Long memberId, String conditionParam) {
        List<LocalDate> recent = memberActivityDayRepository.findRecentActivityDates(memberId);
        if (recent.isEmpty()) {
            return 0L;
        }

        long streak = 1L;
        LocalDate previous = recent.getFirst();
        for (LocalDate date : recent.subList(1, recent.size())) {
            if (!date.equals(previous.minusDays(1))) {
                break;
            }
            streak++;
            previous = date;
        }
        return streak;
    }
}
