package com.lirouti.domain.wallet.service;

import com.lirouti.domain.wallet.enums.Currency;

/**
 * 거래 한 건의 결과. <b>엔티티 대신 이것을 돌려준다.</b>
 *
 * <p>{@code WalletTransaction} 을 그대로 넘기면 호출부가 트랜잭션 밖에서 지연 로딩 필드
 * (예: 회원)를 건드리는 순간 터진다. 특히 멱등으로 되돌려주는 경로는 다른 트랜잭션에서 읽어
 * 온 엔티티라 더 그렇다. 값만 담아 넘기면 그 사고가 아예 일어나지 않는다.
 *
 * @param transactionId 원장에 남은 거래 id. 되돌려준 경우 <b>처음 거래의 id</b> 다.
 * @param applied       이번 호출이 실제로 잔액을 움직였는지. 멱등으로 되돌려준 것이면 {@code false}.
 */
public record WalletResult(
        Long transactionId,
        Currency currency,
        int paidBalance,
        int freeBalance,
        boolean applied
) {
    public int totalBalance() {
        return paidBalance + freeBalance;
    }
}
