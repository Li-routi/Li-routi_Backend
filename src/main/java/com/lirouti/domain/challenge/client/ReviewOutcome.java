package com.lirouti.domain.challenge.client;

/**
 * 인증 사진 심사가 어떻게 끝났는지.
 *
 * <p>예전에는 이 다섯이 {@code decided}(boolean) 하나로 뭉개져 있었다. 그래서 <b>"답을 못
 * 받은 것"과 "일부러 안 부른 것"이 구분되지 않았다.</b> 둘을 같이 다루면 문제가 생긴다 —
 * 모델 오작동 시 쓰라고 만든 킬 스위치가 보류로 빠지면 <b>인증을 막는 스위치</b>가 된다.
 *
 * <p>가르는 기준은 하나다. <b>다시 심사하면 결과가 달라질 수 있는가.</b>
 * 달라질 수 있으면 보류하고, 아니면 지금 결론을 낸다.
 */
public enum ReviewOutcome {

    /** 심사가 통과시켰다. 승격하고 저장한다. */
    APPROVED,

    /** 심사가 반려했다. 422 로 막고 대기본을 지운다. 다시 해도 같으므로 보류하지 않는다. */
    REJECTED,

    /**
     * 심사기가 답을 주지 못했다. 장애·타임아웃·응답 형식 이상, S3 일시 오류가 여기다.
     *
     * <p><b>복구되면 판정이 달라진다.</b> 그래서 이것만 보류 대상이다.
     */
    TRANSIENT_FAILURE,

    /**
     * 심사를 일부러 끈 상태({@code ai.review.enabled = false}).
     *
     * <p>장애가 아니라 <b>운영이 내린 결정</b>이므로 통과시킨다. 이것을 보류로 다루면
     * 킬 스위치를 켠 순간 아무도 인증을 못 하게 된다 — 스위치를 만든 목적과 정반대다.
     */
    DISABLED,

    /**
     * 심사할 수가 없다. 챌린지를 찾지 못했거나, 사진이 심사 상한을 넘었다.
     *
     * <p><b>다시 해도 같으므로</b> 보류하지 않고 통과시킨다. 보류해 봐야 상한까지 시도만
     * 반복하다 결국 통과하는데, 그동안 사용자 사진만 묶어 둔다.
     */
    NOT_APPLICABLE;

    /** 보류 대상인가. 지금은 {@link #TRANSIENT_FAILURE} 하나뿐이다. */
    public boolean shouldHold() {
        return this == TRANSIENT_FAILURE;
    }

    /** 심사가 실제로 판정을 내렸는가. 통과·반려 둘만 해당한다. */
    public boolean decided() {
        return this == APPROVED || this == REJECTED;
    }
}
