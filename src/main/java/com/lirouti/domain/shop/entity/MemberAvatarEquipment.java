package com.lirouti.domain.shop.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.shop.enums.AvatarSlot;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지금 무엇을 입고 있는가. <b>슬롯당 한 행이다.</b>
 *
 * <p><b>벗은 슬롯은 행이 없다.</b> {@code null} 을 넣지 않는다 — "안 입었다" 와 "입었는데
 * 값이 비었다" 가 구분되지 않는다. 가입 직후에는 행이 하나도 없는 것이 정상이다.
 *
 * <p><b>{@code slot} 을 여기 한 번 더 갖는 이유는 유니크 제약 때문이다.</b> 이 값이 없으면
 * "한 슬롯에 하나" 를 DB 로 강제할 수 없다 — 조인해야 알 수 있는 값에는 유니크를 걸 수 없다.
 * 대신 {@code (avatar_item_id, slot)} 복합 FK 로 아이템 쪽 슬롯과 <b>어긋날 수 없게</b> 묶는다.
 */
@Entity
@Getter
@Table(
        name = "member_avatar_equipment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_avatar_equipment_member_slot",
                columnNames = {"member_id", "slot"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAvatarEquipment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot", nullable = false, length = 30)
    private AvatarSlot slot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "avatar_item_id", nullable = false)
    private AvatarItem avatarItem;

    @Builder
    private MemberAvatarEquipment(Member member, AvatarItem avatarItem) {
        this.member = member;
        this.avatarItem = avatarItem;
        // 슬롯은 아이템에서 가져온다. 호출부가 따로 넘기면 아이템의 실제 슬롯과 어긋날 수 있고,
        // 그 조합은 복합 FK 가 거절하므로 애초에 받을 이유가 없다.
        this.slot = avatarItem.getSlot();
    }
}
