package com.lirouti.domain.shop.service.query;

import com.lirouti.domain.shop.converter.ShopConverter;
import com.lirouti.domain.shop.dto.response.ShopResDTO;
import com.lirouti.domain.shop.entity.AvatarItem;
import com.lirouti.domain.shop.entity.MemberAvatarEquipment;
import com.lirouti.domain.shop.enums.AvatarSlot;
import com.lirouti.domain.shop.repository.AvatarItemRepository;
import com.lirouti.domain.shop.repository.MemberAvatarEquipmentRepository;
import com.lirouti.domain.shop.repository.MemberAvatarItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShopQueryService {

    private final AvatarItemRepository avatarItemRepository;
    private final MemberAvatarItemRepository memberAvatarItemRepository;
    private final MemberAvatarEquipmentRepository memberAvatarEquipmentRepository;

    /**
     * 상점 아이템 목록.
     *
     * <p><b>보유한 것도 같은 목록에 섞여 나온다.</b> 화면이 격자 하나이고, 보유 여부는
     * {@code owned} 로 가른다.
     *
     * @param slot      {@code null} 이면 전체. 화면의 "전체" 탭이 이것이다
     * @param ownedOnly 보유한 것만 볼 때
     */
    @Transactional(readOnly = true)
    public ShopResDTO.Items getItems(Long memberId, AvatarSlot slot, boolean ownedOnly) {
        Set<Long> ownedIds = Set.copyOf(memberAvatarItemRepository.findOwnedItemIds(memberId));

        if (ownedOnly) {
            // 보유한 것만 볼 때는 판매 여부를 보지 않는다 — 이미 산 것이다.
            List<AvatarItem> owned = ownedIds.isEmpty()
                    ? List.of()
                    : avatarItemRepository.findAllById(ownedIds).stream()
                            .filter(item -> slot == null || item.getSlot() == slot)
                            .sorted(java.util.Comparator
                                    .comparing(AvatarItem::getSlot)
                                    .thenComparingInt(AvatarItem::getSortOrder)
                                    .thenComparing(AvatarItem::getId))
                            .toList();
            return ShopConverter.toItems(owned, ownedIds);
        }

        // 판매 중인 것 + 보유한 것. 보유했는데 판매가 내려간 아이템이 빠지면, 착장 저장이
        // 전체 목록을 받으므로 화면에서 고를 수 없어 조용히 벗겨진다.
        List<AvatarItem> items = avatarItemRepository.findForShop(
                slot, ownedIds.isEmpty() ? List.of(-1L) : ownedIds);
        return ShopConverter.toItems(items, ownedIds);
    }

    /** 현재 착용 상태. 안 입은 자리는 실리지 않는다. */
    @Transactional(readOnly = true)
    public ShopResDTO.Avatar getMyAvatar(Long memberId) {
        List<MemberAvatarEquipment> equipped =
                memberAvatarEquipmentRepository.findAllByMemberId(memberId);
        return ShopConverter.toAvatar(equipped);
    }
}
