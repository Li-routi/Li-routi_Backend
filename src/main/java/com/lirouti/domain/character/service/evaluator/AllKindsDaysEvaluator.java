package com.lirouti.domain.character.service.evaluator;

import com.lirouti.domain.character.repository.CharacterConditionQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 하루에 개인·모임·챌린지를 <b>모두</b> 완료한 날의 수.
 *
 * <p>원안의 까루는 "알림을 눌러 접속" 이었는데 서버가 알 수 없어 "앱을 골고루 쓴다" 로
 * 옮겼다.
 */
@Component
@RequiredArgsConstructor
public class AllKindsDaysEvaluator implements UnlockConditionEvaluator {

    private final CharacterConditionQueryRepository characterConditionQueryRepository;

    @Override
    public String conditionKey() {
        return "ALL_KINDS_DAYS";
    }

    @Override
    public long count(Long memberId, String conditionParam) {
        return characterConditionQueryRepository.countAllKindsDays(memberId);
    }
}
