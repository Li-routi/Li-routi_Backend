package com.lirouti.domain.wallet.repository;

import com.lirouti.domain.wallet.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    /**
     * 멱등 확인. 이미 처리한 요청이면 그 거래를 그대로 돌려준다.
     *
     * <p>이 조회만으로는 동시 요청을 막지 못한다 — 둘 다 "없다"를 보고 지나갈 수 있다.
     * 최종 방어는 {@code uk_wallet_transaction_idempotency} 유니크 제약이고, 이 조회는
     * 흔한 경우(재시도가 시간차를 두고 오는 경우)를 예외 없이 처리하기 위한 것이다.
     */
    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);
}
