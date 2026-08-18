package com.lirouti.domain.charge.entity;

import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 현금으로 사는 재화 묶음.
 *
 * <p><b>유상분과 보너스를 나눠 갖는다.</b> 충전 팝업이 {@code 550코인} 과 {@code +50 보너스} 를
 * 화면에서 이미 구분한다. 합쳐 두면 나중에 못 쪼갠다 — 환불 가능액(유상)과 소멸 대상(무상)을
 * 가를 수 없다.
 *
 * <p><b>지급 재화는 유료 재화여야 한다.</b> 무료 재화를 파는 상품이 만들어지면 결제는 성공하는데
 * 지급에서 거부된다({@code paid_balance} 를 가질 수 없으므로) — <b>돈은 나갔는데 재화가 안
 * 들어오는</b> 상태가 된다.
 */
@Entity
@Getter
@Table(name = "charge_product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChargeProduct extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_currency", nullable = false, length = 20)
    private Currency rewardCurrency;

    /** 유상으로 들어갈 수량. */
    @Column(name = "reward_amount", nullable = false)
    private int rewardAmount;

    /** 무상으로 들어갈 보너스. */
    @Column(name = "bonus_amount", nullable = false)
    private int bonusAmount;

    @Column(name = "price_krw", nullable = false)
    private int priceKrw;

    @Column(name = "popular", nullable = false)
    private boolean popular;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Builder
    private ChargeProduct(Currency rewardCurrency, int rewardAmount, int bonusAmount,
                          int priceKrw, boolean popular, int sortOrder, boolean active) {
        if (!rewardCurrency.isPaidBalanceAllowed()) {
            throw new IllegalArgumentException(
                    "무료 재화는 현금으로 팔 수 없습니다. 결제는 성공하고 지급만 실패합니다. currency="
                            + rewardCurrency);
        }
        if (rewardAmount < 0 || bonusAmount < 0 || rewardAmount + bonusAmount <= 0) {
            throw new IllegalArgumentException("지급 수량은 음수일 수 없고 합이 1 이상이어야 합니다.");
        }
        if (priceKrw <= 0) {
            throw new IllegalArgumentException("결제 금액은 1 이상이어야 합니다.");
        }
        this.rewardCurrency = rewardCurrency;
        this.rewardAmount = rewardAmount;
        this.bonusAmount = bonusAmount;
        this.priceKrw = priceKrw;
        this.popular = popular;
        this.sortOrder = sortOrder;
        this.active = active;
    }
}
