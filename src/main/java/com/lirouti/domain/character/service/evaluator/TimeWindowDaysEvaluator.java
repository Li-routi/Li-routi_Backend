package com.lirouti.domain.character.service.evaluator;

import com.lirouti.domain.character.repository.CharacterConditionQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * 그 시간대에 완료한 서로 다른 날의 수.
 *
 * <p><b>{@code conditionParam} 은 {@code HH:MM-HH:MM} 이다</b>(KST). 노아는 아침
 * {@code 05:00-07:59}, 모리는 한밤중 {@code 00:00-00:59} 다.
 *
 * <p>원안의 모리는 "자정에 앱에 접속해 있기" 였는데 서버가 알 수 없어 "그 시간에 완료" 로
 * 옮겼다. 성격(남들 안 하는 시간)은 남는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeWindowDaysEvaluator implements UnlockConditionEvaluator {

    private final CharacterConditionQueryRepository characterConditionQueryRepository;

    @Override
    public String conditionKey() {
        return "TIME_WINDOW_DAYS";
    }

    @Override
    public long count(Long memberId, String conditionParam) {
        if (conditionParam == null || !conditionParam.contains("-")) {
            return 0L;
        }

        String[] bounds = conditionParam.split("-", 2);
        try {
            LocalTime from = LocalTime.parse(bounds[0].trim());
            LocalTime to = LocalTime.parse(bounds[1].trim());
            if (to.isBefore(from)) {
                // 자정을 넘는 구간은 지금 조건에 없다. 조용히 미달로 두면 왜 안 열리는지
                // 알 길이 없으므로 남긴다.
                log.warn("자정을 넘는 시간대 조건은 아직 다루지 않습니다. param={}", conditionParam);
                return 0L;
            }
            return characterConditionQueryRepository
                    .countDistinctTimeWindowDays(memberId, from, to);
        } catch (DateTimeParseException exception) {
            log.warn("시간대 조건을 읽지 못했습니다. param={}", conditionParam);
            return 0L;
        }
    }
}
