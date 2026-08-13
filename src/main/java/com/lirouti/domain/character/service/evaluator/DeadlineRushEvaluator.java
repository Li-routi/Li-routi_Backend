package com.lirouti.domain.character.service.evaluator;

import com.lirouti.domain.character.repository.CharacterConditionQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 마감 직전에 완료한 건수. <b>{@code conditionParam} 은 초</b>다(호롱은 60).
 *
 * <p>마감 시각과 같거나 그 뒤는 인정하지 않는다 — "아슬아슬하게 해냈다" 가 조건의 뜻이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadlineRushEvaluator implements UnlockConditionEvaluator {

    private final CharacterConditionQueryRepository characterConditionQueryRepository;

    @Override
    public String conditionKey() {
        return "DEADLINE_RUSH";
    }

    @Override
    public long count(Long memberId, String conditionParam) {
        try {
            int withinSeconds = Integer.parseInt(conditionParam == null ? "" : conditionParam.trim());
            if (withinSeconds <= 0) {
                return 0L;
            }
            return characterConditionQueryRepository.countDeadlineRush(memberId, withinSeconds);
        } catch (NumberFormatException exception) {
            log.warn("마감 임박 조건의 초를 읽지 못했습니다. param={}", conditionParam);
            return 0L;
        }
    }
}
