package com.lirouti.domain.shop.converter;

import com.lirouti.domain.shop.dto.response.ShopResDTO;
import com.lirouti.domain.shop.entity.AvatarItem;
import com.lirouti.domain.shop.entity.MemberAvatarEquipment;
import com.lirouti.domain.shop.enums.ShopCategory;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ShopConverter {

    private ShopConverter() {
    }

    public static ShopResDTO.Category toCategory(ShopCategory category) {
        return ShopResDTO.Category.builder()
                .key(category)
                .name(category.getName())
                .source(category.getSource())
                .slot(category.getSlot())
                .sortOrder(category.getSortOrder())
                .build();
    }

    /** 정렬해서 내린다 — 클라이언트가 순서 규칙을 따로 갖지 않게 한다. */
    public static ShopResDTO.Categories toCategories(List<ShopCategory> categories) {
        return ShopResDTO.Categories.builder()
                .categories(categories.stream()
                        .sorted(Comparator.comparingInt(ShopCategory::getSortOrder))
                        .map(ShopConverter::toCategory)
                        .toList())
                .build();
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

    /** 장착하지 않은 회원도 빈 목록으로 포함해 회원별 현재 착용 상태를 조립한다. */
    public static Map<Long, ShopResDTO.Avatar> toAvatarsByMemberId(
            List<Long> memberIds,
            List<MemberAvatarEquipment> equipments
    ) {
        Map<Long, List<MemberAvatarEquipment>> equipmentsByMemberId = equipments.stream()
                .collect(Collectors.groupingBy(equipment -> equipment.getMember().getId()));

        return memberIds.stream().distinct().collect(Collectors.toMap(
                Function.identity(),
                memberId -> toAvatar(equipmentsByMemberId.getOrDefault(memberId, List.of())),
                (left, right) -> left,
                LinkedHashMap::new
        ));
    }
}
