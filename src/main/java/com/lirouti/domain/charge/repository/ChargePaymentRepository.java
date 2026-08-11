package com.lirouti.domain.charge.repository;

import com.lirouti.domain.charge.entity.ChargePayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChargePaymentRepository extends JpaRepository<ChargePayment, Long> {

    /**
     * 검증 대상을 <b>인증된 회원의 것으로 좁혀</b> 찾는다.
     *
     * <p>회원을 안 보면 남의 {@code paymentId} 를 알아낸 사람이 자기 계정으로 완료를 부를 수
     * 있다 — 검증은 전부 통과하는데 재화는 <b>부른 사람에게</b> 들어간다.
     */
    Optional<ChargePayment> findByPaymentIdAndMemberId(String paymentId, Long memberId);

    /**
     * 웹훅용. 회원을 모르므로 결제 식별자로만 찾는다.
     *
     * <p><b>행을 잠근다.</b> 완료 요청과 웹훅이 동시에 들어와도 하나만 지급하도록,
     * 상태 확인과 전이가 같은 잠금 안에서 일어나야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ChargePayment p where p.paymentId = :paymentId")
    Optional<ChargePayment> findByPaymentIdForUpdate(@Param("paymentId") String paymentId);

    boolean existsByTxId(String txId);
}
