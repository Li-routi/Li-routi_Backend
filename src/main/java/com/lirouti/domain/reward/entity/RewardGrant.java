package com.lirouti.domain.reward.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.reward.enums.RewardReason;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지금 유효한 지급 하나.
 *
 * <p><b>이력이 아니라 상태다.</b> 회수하면 이 행은 지워지고, 무슨 일이 있었는지는
 * {@code wallet_transaction} 에 남는다. 잔액과 원장을 나눈 것과 같은 구조다.
 *
 * <p>합치지 않은 이유는 <b>재지급</b> 때문이다. 원장은 행을 지우지 않는 것이 원칙인데, 회수
 * 뒤 다시 인증하면 지급이 되살아나야 한다. 원장 하나로 두면 그 인증에 대한 멱등 키가 이미
 * 있어 재지급이 조용히 건너뛰어진다.
 */
@Entity
@Getter
@Table(
        name = "reward_grant",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reward_grant_reason_reference",
                columnNames = {"member_id", "reason", "reference_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RewardGrant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private RewardReason reason;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 20)
    private Currency currency;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Builder
    private RewardGrant(Member member, RewardReason reason, Long referenceId,
                        Currency currency, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("지급 수량은 1 이상이어야 합니다.");
        }
        this.member = member;
        this.reason = reason;
        this.referenceId = referenceId;
        this.currency = currency;
        this.amount = amount;
    }

    /**
     * 지갑에 넘길 멱등 키.
     *
     * <p><b>인증 id 가 아니라 이 행의 id 로 만든다.</b> 인증 id 로 만들면 회수 뒤 재지급이 같은
     * 키가 되어 막힌다 — 지급 행은 회수 때 지워지고 재지급 때 새 id 로 생기므로, 이 키는 자연히
     * 매번 달라진다.
     */
    public String idempotencyKey() {
        return "reward:grant:" + id;
    }

    /** 회수 거래의 멱등 키. 지급과 같은 행을 가리키되 방향이 반대라 접두사로 가른다. */
    public String clawbackIdempotencyKey() {
        return "reward:clawback:" + id;
    }
}
