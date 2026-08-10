package com.lirouti.domain.wallet.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.domain.wallet.enums.WalletTransactionType;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 거래 원장. 잔액이 움직인 모든 기록이다.
 *
 * <p><b>행을 고치거나 지우지 않는다.</b> 잘못된 거래는 반대 방향 거래를 새로 쌓아 상쇄한다
 * (회계 원장과 같다). 고쳐 쓰면 "그때 무슨 일이 있었나"가 사라지는데, 현금이 오가는 데이터라
 * 그 기록이 없으면 분쟁에 답할 수 없다.
 *
 * <p>수정 메서드가 하나도 없는 것은 실수가 아니다.
 */
@Entity
@Getter
@Table(
        name = "wallet_transaction",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wallet_transaction_idempotency",
                columnNames = {"member_id", "idempotency_key"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 20)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private WalletTransactionType transactionType;

    /** 유상 잔액 증감. 차감이면 음수다. */
    @Column(name = "paid_delta", nullable = false)
    private int paidDelta;

    /** 무상 잔액 증감. 차감이면 음수다. */
    @Column(name = "free_delta", nullable = false)
    private int freeDelta;

    @Column(name = "paid_balance_after", nullable = false)
    private int paidBalanceAfter;

    @Column(name = "free_balance_after", nullable = false)
    private int freeBalanceAfter;

    /**
     * 멱등 키. <b>호출부가 뜻이 담기게 만든다.</b>
     *
     * <p>예를 들어 인증 하나에 대한 리워드는 그 인증을 가리키는 키 하나뿐이라, 같은 인증으로
     * 두 번 지급하는 길이 아예 없다. 네트워크가 끊겨 클라이언트가 재시도하는 것은 정상
     * 동작이고, 그때 두 번 반영되면 그대로 돈 문제가 된다.
     *
     * <p><b>유니크는 회원 범위다</b>({@code member_id} + 이 값). 전역으로 두면 호출부가
     * 자연스럽게 만드는 키("아이템 7 구매")가 회원을 담지 않아, <b>다른 회원이 같은 아이템을
     * 살 때 이미 처리된 요청으로 취급되어 차감이 조용히 건너뛰어진다.</b> 호출부는 성공으로
     * 보고 물건을 내주므로 돈만 새고 에러는 나지 않는다.
     *
     * <p>대신 <b>회원을 넘나드는 중복은 이 제약이 막지 못한다.</b> 같은 결제 영수증이 두
     * 계정에 쓰이는 것 같은 경우는 영수증 자체에 유니크를 거는 쪽(결제 기록)이 막아야 한다.
     */
    @Column(name = "idempotency_key", nullable = false, length = 150)
    private String idempotencyKey;

    /** 근거가 된 대상의 종류. 무엇 때문에 움직였는지 되짚는 용도라 선택 값이다. */
    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Builder
    private WalletTransaction(
            Member member,
            Currency currency,
            WalletTransactionType transactionType,
            int paidDelta,
            int freeDelta,
            int paidBalanceAfter,
            int freeBalanceAfter,
            String idempotencyKey,
            String referenceType,
            Long referenceId
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("멱등 키 없이 거래를 남길 수 없습니다.");
        }
        this.member = member;
        this.currency = currency;
        this.transactionType = transactionType;
        this.paidDelta = paidDelta;
        this.freeDelta = freeDelta;
        this.paidBalanceAfter = paidBalanceAfter;
        this.freeBalanceAfter = freeBalanceAfter;
        this.idempotencyKey = idempotencyKey;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
    }
}
