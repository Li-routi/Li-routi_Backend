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

    /** 구매 시점 결제 재화. 마스터가 바뀌어도 이 값은 그대로다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 20)
    private Currency currency;

    /** 구매 시점 단가. */
    @Column(name = "paid_price", nullable = false)
    private int paidPrice;

    @Column(name = "purchased_at", nullable = false)
    private LocalDateTime purchasedAt;

    @Builder
    private MemberAvatarItem(Member member, AvatarItem avatarItem,
                             Currency currency, int paidPrice, LocalDateTime purchasedAt) {
        this.member = member;
        this.avatarItem = avatarItem;
        this.currency = currency;
        this.paidPrice = paidPrice;
        this.purchasedAt = purchasedAt;
    }
}
