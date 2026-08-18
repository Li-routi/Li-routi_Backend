package com.lirouti.domain.shop.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 구매 한 건. <b>여러 아이템과 여러 재화가 이 아래로 묶인다.</b>
 *
 * <p>이 표가 없으면 구매가 실체를 갖지 못한다 — 여섯 개를 한 번에 사면 {@code member_avatar_item}
 * 여섯 행과 {@code wallet_transaction} 한두 행으로 흩어지고, 그것을 하나로 묶는 단서가
 * {@code purchased_at} 타임스탬프뿐이다. 같은 초에 두 구매가 들어오면 그마저 무너진다.
 *
 * <p><b>멱등성만이 이유가 아니다.</b> 백오피스의 구매 내역 조회·CS 문의 대조·환불이 전부
 * "이 구매" 를 가리킬 수 있어야 한다.
 *
 * <p><b>결제 금액을 들지 않는다.</b> {@code wallet_transaction} 이 이미
 * {@code (회원, 재화, "avatar:purchase:" + 키)} 로 갖고 있어서, 이 행 + 그 아이템들 + 키로 찾은
 * 지갑 거래면 완전한 결과가 된다. 여기 복사하면 그것이 두 번째 진실이 되고, 어긋났을 때 무엇이
 * 맞는지 정할 방법이 없다.
 */
@Entity
@Getter
@Table(
        name = "avatar_purchase",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_avatar_purchase_idempotency",
                columnNames = {"member_id", "idempotency_key"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvatarPurchase extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** 클라이언트가 만든다. 요청 DTO 가 64자로 제한한다. */
    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    /**
     * 장바구니를 가리키는 값.
     *
     * <p>같은 키로 <b>다른</b> 장바구니가 들어온 것을 가려내려고 둔다. 지갑은 같은 키를 "이미
     * 처리한 요청" 으로 보아 차감 없이 예전 결과를 돌려주는데, 그 전제는 "같은 요청" 이다.
     * 장바구니가 달라졌는데 키가 같으면 전제가 깨져 <b>보유 행만 생기고 값이 빠지지 않는다.</b>
     */
    @Column(name = "item_fingerprint", nullable = false, length = 64)
    private String itemFingerprint;

    @Column(name = "purchased_at", nullable = false)
    private LocalDateTime purchasedAt;

    @Builder
    private AvatarPurchase(Member member, String idempotencyKey,
                           String itemFingerprint, LocalDateTime purchasedAt) {
        this.member = member;
        this.idempotencyKey = idempotencyKey;
        this.itemFingerprint = itemFingerprint;
        this.purchasedAt = purchasedAt;
    }

    /** 같은 장바구니로 다시 들어온 요청인가. 다르면 같은 키를 다른 구매에 재사용한 것이다. */
    public boolean hasSameItems(String fingerprint) {
        return this.itemFingerprint.equals(fingerprint);
    }
}
