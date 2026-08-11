package com.lirouti.domain.charge.enums;

/**
 * 충전 결제 한 건의 상태.
 *
 * <p><b>취소 상태를 두지 않는다.</b> 이 단계에서는 환불·취소를 구현하지 않기로 했고,
 * <b>기록하지 않을 상태를 값으로 만들지 않는다.</b> 환불을 붙이는 시점에 함께 정한다.
 */
public enum ChargePaymentStatus {

    /** 결제 시작만 한 상태. 아직 돈이 오가지 않았다. */
    READY,

    /** 검증까지 끝나 재화를 지급했다. */
    PAID,

    /** 검증에 실패했거나 결제가 실패했다. */
    FAILED
}
