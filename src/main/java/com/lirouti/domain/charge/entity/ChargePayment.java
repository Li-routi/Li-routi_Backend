package com.lirouti.domain.charge.entity;

import com.lirouti.domain.charge.enums.ChargePaymentStatus;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 충전 결제 한 건.
 *
 * <p><b>지급 내용을 결제 시작 시점에 복사해 둔다.</b> 상품 id 만 두고 지급 때 다시 읽으면,
 * 결제를 시작한 뒤 운영이 상품을 고치는 순간 어긋난다 — 금액 검증은 {@code expectedAmount} 로
 * 하니 통과하는데 <b>지급 수량이나 재화가 달라진다.</b> 아바타 아이템이 구매 시점 단가를
 * 스냅샷으로 남긴 것과 같은 이유다.
 *
 * <p><b>식별자가 둘이다.</b> 포트원 V2 를 쓰므로 {@code paymentId} 는 <b>서버가 결제 전에</b>
 * 만들고, {@code txId} 는 포트원이 결제 뒤에 알려준다.
 */
@Entity
@Getter
@Table(
        name = "charge_payment",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_charge_payment_payment_id", columnNames = "payment_id"),
                // 같은 결제로 두 번 지급되면 그대로 손해다. 애플리케이션 분기로만 막으면 뚫린다.
                @UniqueConstraint(name = "uk_charge_payment_tx_id", columnNames = "tx_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChargePayment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charge_product_id", nullable = false)
    private ChargeProduct chargeProduct;

    /** 서버가 결제 전에 만드는 식별자. 포트원 V2 의 {@code paymentId} 다. */
    @Column(name = "payment_id", nullable = false, length = 64)
    private String paymentId;

    /** 포트원이 만드는 거래 식별자. 결제 전에는 비어 있다. */
    @Column(name = "tx_id", length = 64)
    private String txId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChargePaymentStatus status;

    /** 결제 시작 시점에 서버가 기록한 금액. <b>검증에서 이 값과 정확히 대조한다.</b> */
    @Column(name = "expected_amount", nullable = false)
    private int expectedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_currency", nullable = false, length = 20)
    private Currency rewardCurrency;

    @Column(name = "reward_amount", nullable = false)
    private int rewardAmount;

    @Column(name = "bonus_amount", nullable = false)
    private int bonusAmount;

    @Column(name = "paid_amount")
    private Integer paidAmount;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "fail_reason", length = 255)
    private String failReason;

    @Builder
    private ChargePayment(Member member, ChargeProduct product,
                          String paymentId, LocalDateTime requestedAt) {
        this.member = member;
        this.chargeProduct = product;
        this.paymentId = paymentId;
        this.status = ChargePaymentStatus.READY;
        this.requestedAt = requestedAt;
        // 지급 내용을 여기서 굳힌다. 이후 상품이 바뀌어도 이 값으로 지급한다.
        this.expectedAmount = product.getPriceKrw();
        this.rewardCurrency = product.getRewardCurrency();
        this.rewardAmount = product.getRewardAmount();
        this.bonusAmount = product.getBonusAmount();
    }

    public boolean isReady() {
        return status == ChargePaymentStatus.READY;
    }

    public boolean isPaid() {
        return status == ChargePaymentStatus.PAID;
    }

    /**
     * 검증을 통과해 지급까지 끝났다.
     *
     * <p><b>{@code READY} 일 때만 넘어간다.</b> 웹훅과 완료 요청이 동시에 들어오면 하나만
     * 통과해야 한다 — 읽고 나서 쓰면 둘 다 지나간다.
     */
    public boolean markPaid(String txId, int paidAmount, LocalDateTime paidAt) {
        if (!isReady()) {
            return false;
        }
        this.txId = txId;
        this.paidAmount = paidAmount;
        this.paidAt = paidAt;
        this.status = ChargePaymentStatus.PAID;
        return true;
    }

    public boolean markFailed(String reason, LocalDateTime failedAt) {
        if (!isReady()) {
            return false;
        }
        this.failReason = reason;
        this.failedAt = failedAt;
        this.status = ChargePaymentStatus.FAILED;
        return true;
    }
}
