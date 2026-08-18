package com.lirouti.domain.character.service.evaluator;

import com.lirouti.domain.character.repository.CharacterConditionQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 지금 좋아요를 눌러 둔 <b>남의 인증</b> 수.
 *
 * <p><b>누적이 아니다.</b> 좋아요 취소가 하드 삭제라 이력이 없어 누적은 셀 수 없다. 대신
 * 취소가 행을 지우므로 목표 수만큼을 <b>동시에 유지</b>해야 하고, 원안이 막으려던 "눌렀다
 * 취소하기를 반복해 채우기" 가 규칙 없이 막힌다.
 *
 * <p>조건에 닿는 순간 해금되고, 그 뒤 취소해도 해금은 되돌리지 않는다.
 */
@Component
@RequiredArgsConstructor
public class LikeGivenEvaluator implements UnlockConditionEvaluator {

    private final CharacterConditionQueryRepository characterConditionQueryRepository;

    @Override
    public String conditionKey() {
        return "LIKE_GIVEN";
    }

    @Override
    public long count(Long memberId, String conditionParam) {
        return characterConditionQueryRepository.countLikesGiven(memberId);
    }
}
