package com.lirouti.domain.shop.dto.response;

import com.lirouti.domain.shop.enums.AvatarSlot;
import com.lirouti.domain.shop.enums.ShopCategory;
import com.lirouti.domain.shop.enums.ShopCategorySource;
import com.lirouti.domain.wallet.enums.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

public final class ShopResDTO {

    private ShopResDTO() {
    }

    @Schema(name = "ShopCategory", description = "상점 화면의 탭 하나")
    @Builder
    public record Category(
            @Schema(description = "탭 식별자", example = "HEAD") ShopCategory key,
            @Schema(description = "화면에 쓸 이름", example = "머리") String name,
            @Schema(description = """
                    이 탭을 눌렀을 때 무엇을 가져오는가. `ITEM` 이면 아이템 목록,
                    `CHARACTER` 면 캐릭터 목록이다. **`slot` 이 비어 있는 탭이 둘이라
                    이 값으로 갈라야 한다.**""")
            ShopCategorySource source,
            @Schema(description = """
                    아이템 목록을 요청할 때 넣을 슬롯. **비어 있으면 넣지 않는다** —
                    `전체` 탭이 곧 필터 없음이다.""")
            AvatarSlot slot,
            @Schema(description = "표시 순서. 오름차순으로 이미 정렬돼 있다") int sortOrder
    ) {
    }

    @Schema(name = "ShopCategories", description = "상점 탭 목록")
    @Builder
    public record Categories(
            @Schema(description = "탭 목록. 받은 순서대로 그리면 된다") List<Category> categories
    ) {
    }

    @Schema(name = "ShopAvatarItem", description = "상점 아이템 한 건")
    @Builder
    public record Item(
            @Schema(description = "아이템 id") Long id,
            @Schema(description = "차지하는 자리") AvatarSlot slot,
            @Schema(description = "이름") String name,
            @Schema(description = "이미지 주소") String imageUrl,
            @Schema(description = "결제 재화") Currency currency,
            @Schema(description = "가격") int price,
            @Schema(description = "이미 보유한 아이템인가. 보유했으면 가격 대신 보유로 표시한다")
            boolean owned,
            @Schema(description = "판매 중인가. 보유했지만 판매가 내려간 아이템은 false 다")
            boolean onSale
    ) {
    }

    @Schema(name = "ShopAvatarItems", description = "상점 아이템 목록")
    @Builder
    public record Items(
            @Schema(description = "아이템 목록") List<Item> items
    ) {
    }

    @Schema(name = "AvatarEquippedItem", description = "착용 중인 아이템 한 건")
    @Builder
    public record Equipped(
            @Schema(description = "자리") AvatarSlot slot,
            @Schema(description = "아이템 id") Long itemId,
            @Schema(description = "이름") String name,
            @Schema(description = "이미지 주소") String imageUrl
    ) {
    }

    /**
     * 현재 착용 상태.
     *
     * <p><b>안 입은 자리는 목록에 없다.</b> 가입 직후에는 빈 목록이 정상이다 — 기본 제공
     * 아이템을 두지 않기로 했다.
     */
    @Schema(name = "MemberAvatar", description = "내 아바타 착용 상태")
    @Builder
    public record Avatar(
            @Schema(description = "착용 중인 아이템. 안 입은 자리는 실리지 않는다")
            List<Equipped> equipped
    ) {
    }
}
