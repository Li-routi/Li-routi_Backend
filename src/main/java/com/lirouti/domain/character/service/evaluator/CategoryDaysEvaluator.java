package com.lirouti.domain.character.service.evaluator;

import com.lirouti.domain.character.enums.RoutineCategoryKey;
import com.lirouti.domain.character.repository.CharacterConditionQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 그 카테고리를 한 서로 다른 날의 수.
 *
 * <p><b>{@code conditionParam} 에 쉼표를 쓰면 합집합이다.</b> 코코가 "운동 또는 건강" 인데,
 * 조건 행을 둘로 나누면 AND 가 되어 뜻이 뒤집힌다.
 */
@Component
@RequiredArgsConstructor
public class CategoryDaysEvaluator implements UnlockConditionEvaluator {

    private final CharacterConditionQueryRepository characterConditionQueryRepository;

    @Override
    public String conditionKey() {
        return "CATEGORY_DAYS";
    }

    @Override
    public long count(Long memberId, String conditionParam) {
        if (conditionParam == null || conditionParam.isBlank()) {
            return 0L;
        }

        List<RoutineCategoryKey> keys = Arrays.stream(conditionParam.split(","))
                .map(String::trim)
                .map(RoutineCategoryKey::from)
                .flatMap(Optional::stream)
                .toList();
        if (keys.isEmpty()) {
            return 0L;
        }

        return characterConditionQueryRepository.countDistinctCategoryDays(
                memberId,
                keys.stream().map(RoutineCategoryKey::getPresetCategoryId).toList(),
                keys.stream().map(key -> key.getChallengeCategory().name()).toList());
    }
}
