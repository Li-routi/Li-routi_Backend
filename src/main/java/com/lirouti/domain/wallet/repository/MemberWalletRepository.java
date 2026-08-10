package com.lirouti.domain.wallet.repository;

import com.lirouti.domain.wallet.entity.MemberWallet;
import com.lirouti.domain.wallet.enums.Currency;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberWalletRepository extends JpaRepository<MemberWallet, Long> {

    /** 잔액 조회용. 잠그지 않는다 — 읽기만 하는 경로가 쓰기를 기다릴 이유가 없다. */
    List<MemberWallet> findAllByMemberId(Long memberId);

    Optional<MemberWallet> findByMemberIdAndCurrency(Long memberId, Currency currency);

    /**
     * 잔액을 바꾸기 전에 잠근다.
     *
     * <p><b>잔액은 유니크 제약으로 막을 수 없다.</b> 이 저장소는 "애플리케이션에서 검사하고
     * 저장" 사이에 동시 요청 두 건이 모두 통과하는 것을 이미 겪었다(챌린지 재참여·인증).
     * 잔액에서 그 일이 벌어지면 잔액이 음수가 되거나 같은 재화를 두 번 쓰게 된다.
     *
     * <p>낙관 잠금이 아니라 비관 잠금인 이유는, 실패 시 재시도를 호출부마다 짜야 하는 쪽보다
     * 기다리는 쪽이 단순하고 이 프로젝트의 다른 잠금과도 방식이 같기 때문이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from MemberWallet w where w.member.id = :memberId and w.currency = :currency")
    Optional<MemberWallet> findForUpdate(@Param("memberId") Long memberId,
                                         @Param("currency") Currency currency);
}
