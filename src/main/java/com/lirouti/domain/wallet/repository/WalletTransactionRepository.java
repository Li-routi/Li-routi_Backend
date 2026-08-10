package com.lirouti.domain.wallet.repository;

import com.lirouti.domain.wallet.entity.WalletTransaction;
import com.lirouti.domain.wallet.enums.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    /**
     * 멱등 확인. 이미 처리한 <b>이 회원의</b> 요청이면 그 거래를 그대로 돌려준다.
     *
     * <p>회원으로 좁히지 않으면 다른 사람이 같은 키를 쓴 거래를 집어 온다. 호출부가 만드는
     * 키는 대개 회원을 담지 않으므로("아이템 7 구매") 그 일이 실제로 벌어진다.
     *
     * <p>재화로도 좁힌다. 교환처럼 한 사건이 두 재화를 건드릴 때 같은 키를 쓰는 것이
     * 자연스러운데, 재화를 안 보면 들어오는 쪽이 이미 처리된 요청이 되어 버린다.
     *
     * <p>이 조회만으로는 동시 요청을 막지 못한다 — 둘 다 "없다"를 보고 지나갈 수 있다.
     * 최종 방어는 {@code uk_wallet_transaction_idempotency} 유니크 제약이고, 이 조회는
     * 흔한 경우(재시도가 시간차를 두고 오는 경우)를 예외 없이 처리하기 위한 것이다.
     */
    Optional<WalletTransaction> findByMemberIdAndCurrencyAndIdempotencyKey(
            Long memberId, Currency currency, String idempotencyKey);
}
