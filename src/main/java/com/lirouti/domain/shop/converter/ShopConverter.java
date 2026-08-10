package com.lirouti.domain.shop.converter;

import com.lirouti.domain.shop.dto.response.ShopResDTO;
import com.lirouti.domain.shop.entity.AvatarItem;
import com.lirouti.domain.shop.entity.MemberAvatarEquipment;

import java.util.List;
import java.util.Set;

public final class ShopConverter {

    private ShopConverter() {
    }

    public static ShopResDTO.Item toItem(AvatarItem item, boolean owned) {
        return ShopResDTO.Item.builder()
                .id(item.getId())
                .slot(item.getSlot())
                .name(item.getName())
                .imageUrl(item.getImageUrl())
                .currency(item.getCurrency())
                .price(item.getPrice())
                .owned(owned)
                .onSale(item.isActive())
                .build();
    }

    public static ShopResDTO.Items toItems(List<AvatarItem> items, Set<Long> ownedItemIds) {
        return ShopResDTO.Items.builder()
                .items(items.stream()
                        .map(item -> toItem(item, ownedItemIds.contains(item.getId())))
                        .toList())
                .build();
    }

    public static ShopResDTO.Equipped toEquipped(MemberAvatarEquipment equipment) {
        AvatarItem item = equipment.getAvatarItem();
        return ShopResDTO.Equipped.builder()
                .slot(equipment.getSlot())
                .itemId(item.getId())
                .name(item.getName())
                .imageUrl(item.getImageUrl())
                .build();
    }

    /** 안 입은 자리는 실리지 않는다. 빈 목록이 정상 상태다. */
    public static ShopResDTO.Avatar toAvatar(List<MemberAvatarEquipment> equipments) {
        return ShopResDTO.Avatar.builder()
                .equipped(equipments.stream().map(ShopConverter::toEquipped).toList())
                .build();
    }
}
