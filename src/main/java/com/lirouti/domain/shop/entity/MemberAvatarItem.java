package com.lirouti.domain.shop.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.shop.enums.AvatarItemSource;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 누가 무엇을 갖고 있는가. <b>영구 보유다.</b>
 *
 * <p><b>구매 시점의 단가와 재화를 스냅샷으로 남긴다.</b> 운영이 가격을 내리거나 결제 재화를
 * 바꿔도 "얼마에 샀나" 가 따라 움직이면 안 된다. 인증이 참여 회차를 스냅샷으로 갖는 것과 같다.
 *
 * <p>소프트 삭제를 두지 않는다 — <b>보유는 취소되지 않는다.</b> 환불이 생기면 그때 별도 설계가
 * 필요하다.
 */
@Entity
@Getter
@Table(
        name = "member_avatar_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_avatar_item_member_item",
                columnNames = {"member_id", "avatar_item_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAvatarItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "avatar_item_id", nullable = false)
    private AvatarItem avatarItem;

    /**
     * 어느 구매에서 왔는가.
     *
     * <p><b>{@code null} 은 "구매 이력을 남기기 전에 산 것" 하나만 뜻한다.</b> 새로 만드는 행은
     * 반드시 채운다 — 비우면 구매 단위 연결이 다시 끊긴다.
     *
     * <p>컬럼이 {@code null} 을 허용하는 것은 기존 행 때문이지 선택이 아니다. 그 행들은
     * 백필하지 않는다 — 묶을 근거가 {@code purchasedAt} 뿐인데, 그것으로 구매를 가를 수 없다는
     * 것이 {@link AvatarPurchase} 를 만드는 이유다. 같은 초에 두 구매가 들어왔으면 <b>잘못된
     * 구매에 아이템이 붙는다.</b>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avatar_purchase_id")
    private AvatarPurchase avatarPurchase;

    /** 구매 시점 결제 재화. 마스터가 바뀌어도 이 값은 그대로다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 20)
    private Currency currency;

    /** 구매 시점 단가. */
    @Column(name = "paid_price", nullable = false)
    private int paidPrice;

    @Column(name = "purchased_at", nullable = false)
    private LocalDateTime purchasedAt;

    // MemberAvatarItem.java
    @Column(name = "grant_reason", length = 30)
    private String grantReason;

    @Builder
    private MemberAvatarItem(Member member, AvatarItem avatarItem, AvatarPurchase avatarPurchase,
                             Currency currency, int paidPrice, LocalDateTime purchasedAt, String grantReason) {
        this.member = member;
        this.avatarItem = avatarItem;
        this.avatarPurchase = avatarPurchase;
        this.currency = currency;
        this.paidPrice = paidPrice;
        this.purchasedAt = purchasedAt;
        this.grantReason = grantReason;
    }

    @Builder
    private MemberAvatarItem(Member member, AvatarItem avatarItem, AvatarPurchase avatarPurchase,
                             Currency currency, int paidPrice, LocalDateTime purchasedAt) {
        this.member = member;
        this.avatarItem = avatarItem;
        this.avatarPurchase = avatarPurchase;
        this.currency = currency;
        this.paidPrice = paidPrice;
        this.purchasedAt = purchasedAt;
    }
}
