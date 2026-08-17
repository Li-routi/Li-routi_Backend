package com.lirouti.domain.shop.entity;

import com.lirouti.domain.shop.enums.AvatarSlot;
import com.lirouti.domain.wallet.enums.Currency;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상점에서 파는 아바타 아이템. <b>운영이 채우고 앱은 읽기만 한다.</b>
 *
 * <p><b>결제 재화를 아이템이 갖는다.</b> 무엇을 어느 재화로 파는지는 상점이 정하고, 지갑은
 * 그것을 해석하지 않는다.
 *
 * <p>마스터 데이터라 소프트 삭제를 두지 않는다. 판매를 내릴 때는 {@code active} 를 내린다 —
 * <b>이미 산 사람의 보유 행이 가리키는 대상이 사라지면 안 된다.</b>
 */
@Entity
@Getter
@Table(
        name = "avatar_item",
        uniqueConstraints = @UniqueConstraint(
                // 기본 키가 있어 중복 방지에는 필요 없다. member_avatar_equipment 가
                // (avatar_item_id, slot) 복합 FK 로 참조할 대상이라 둔다.
                name = "uk_avatar_item_id_slot",
                columnNames = {"id", "slot"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvatarItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot", nullable = false, length = 30)
    private AvatarSlot slot;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 20)
    private Currency currency;

    /** 가격. 기본 제공 아이템을 두지 않으므로 0 일 수 없다. */
    @Column(name = "price", nullable = false)
    private int price;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * 이미지의 <b>S3 key</b>. 절대 URL 이 아니다.
     *
     * <p>오리진이 설정 하나로 갈리므로 조회에서 {@code resolveViewUrl} 로 조립한다. 절대
     * URL 을 담으면 도메인을 바꾸거나 CDN 을 붙이는 순간 이 컬럼의 값이 전부 낡는다.
     */
    @Column(name = "image_key", nullable = false, length = 512)
    private String imageKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Builder
    private AvatarItem(AvatarSlot slot, Currency currency, int price,
                       String name, String imageKey, int sortOrder, boolean active) {
        if (price <= 0) {
            throw new IllegalArgumentException("아이템 가격은 1 이상이어야 합니다.");
        }
        this.slot = slot;
        this.currency = currency;
        this.price = price;
        this.name = name;
        this.imageKey = imageKey;
        this.sortOrder = sortOrder;
        this.active = active;
    }
}
