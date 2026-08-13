package com.lirouti.domain.character.service.evaluator;

/**
 * 조건 하나가 지금 얼마나 찼는지 센다.
 *
 * <p><b>판정기는 조건을 해석하지 않는다.</b> 목표 수치와 "다 찼는가" 판단은 해금 서비스가
 * 하고, 여기서는 세기만 한다 — 그래야 진행도 표시(3/20)도 같은 값으로 나온다.
 *
 * <p><b>조건 키가 자바 enum 이 아니라 문자열인 이유가 여기 있다.</b> 백오피스에서 조건을 걸어
 * 캐릭터를 추가하려는 요구가 있어, 판정기가 없는 키가 데이터에 들어올 수 있다. 그때는 해당
 * 판정기가 없다는 뜻이므로 <b>조용히 미달로 본다</b> — 판정이 터지면 인증 자체가 실패한다.
 */
public interface UnlockConditionEvaluator {

    /** {@code character_unlock_condition.condition_key} 와 맞춘다. */
    String conditionKey();

    /**
     * @param conditionParam 키마다 뜻이 다르다. 없으면 {@code null}
     * @return 지금까지 찬 수치
     */
    long count(Long memberId, String conditionParam);
}
