package com.lirouti.domain.wallet.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원별·재화별 잔액.
 *
 * <p><b>이 값은 원장({@link WalletTransaction})의 합계 캐시다.</b> 거래가 쌓일수록 매번 전부
 * 더하는 비용이 커지는데, 상점 헤더의 잔액은 모든 화면에 떠 있어 조회가 잦다. 그래서 합계를
 * 따로 들고 있되 <b>원장과 반드시 같은 트랜잭션에서 갱신한다</b> — 한쪽만 쓰이면 그 순간부터
 * 두 숫자가 서로 다른 말을 한다.
 */
@Entity
@Getter
@Table(
        name = "member_wallet",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_wallet_member_currency",
                columnNames = {"member_id", "currency"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberWallet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 20)
    private Currency currency;

    /** 유상 잔액. 현금으로 산 것이라 미사용분은 청약철회 대상이다. */
    @Column(name = "paid_balance", nullable = false)
    private int paidBalance;

    /** 무상 잔액. 보너스·리워드·이벤트로 받은 것. */
    @Column(name = "free_balance", nullable = false)
    private int freeBalance;

    @Builder
    private MemberWallet(Member member, Currency currency) {
        this.member = member;
        this.currency = currency;
        this.paidBalance = 0;
        this.freeBalance = 0;
    }

    public int totalBalance() {
        return paidBalance + freeBalance;
    }

    /**
     * 지급. 유상·무상을 나눠 받는다.
     *
     * <p>합쳐서 받으면 어느 쪽이 늘었는지 기록할 수 없다. 충전은 결제분과 보너스가 함께
     * 들어오므로 한 번의 지급이 양쪽을 동시에 올릴 수 있다.
     */
    public void grant(int paidAmount, int freeAmount) {
        if (paidAmount < 0 || freeAmount < 0) {
            throw new IllegalArgumentException("지급 수량은 음수일 수 없습니다.");
        }
        this.paidBalance += paidAmount;
        this.freeBalance += freeAmount;
    }

    /**
     * 차감. <b>무상부터 쓰고 모자란 만큼만 유상에서 뺀다.</b>
     *
     * <p>유상 잔액을 남겨 두어야 환불 요구에 그대로 응할 수 있고, 무상에는 소멸 정책이 붙을
     * 수 있어 먼저 쓰는 것이 사용자에게 유리하다. <b>순서를 반대로 하면 같은 소비를 해도
     * 환불 가능액이 줄어 사용자가 손해를 본다.</b>
     *
     * @return 실제로 빠진 유상·무상 수량. 원장에 그대로 기록한다.
     */
    public Deduction deduct(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("차감 수량은 1 이상이어야 합니다.");
        }
        if (totalBalance() < amount) {
            throw new IllegalStateException("잔액이 부족합니다.");
        }
        int fromFree = Math.min(freeBalance, amount);
        int fromPaid = amount - fromFree;
        this.freeBalance -= fromFree;
        this.paidBalance -= fromPaid;
        return new Deduction(fromPaid, fromFree);
    }

    public boolean canAfford(int amount) {
        return totalBalance() >= amount;
    }

    /** 차감이 유상·무상에서 각각 얼마씩 빠졌는지. */
    public record Deduction(int fromPaid, int fromFree) {
    }
}
