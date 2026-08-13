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

    /**
     * 한 재화에서 빠져나간 몫.
     *
     * <p><b>재화마다 한 줄이다.</b> 재화가 섞인 구매는 차감도 재화별로 나뉘고, 화면의 잔액
     * 표시도 재화별이라 합쳐서 내릴 수 있는 값이 아니다.
     */
    @Schema(name = "ShopPurchasePayment", description = "재화 한 종류의 결제 내역")
    @Builder
    public record Payment(
            @Schema(description = "결제한 재화") Currency currency,
            @Schema(description = "이번에 빠진 수량") int paidAmount,
            @Schema(description = "결제 후 잔액. 화면 상단 잔액을 이 값으로 갱신하면 된다")
            int balanceAfter
    ) {
    }

    /**
     * 구매 결과.
     *
     * <p><b>착용 상태를 싣지 않는다.</b> 일괄 구매는 입히지 않기 때문이다 — 같은 자리 아이템을
     * 둘 이상 함께 사면 어느 쪽을 입힐지 정할 수 없다. 착용은 착장 저장이 맡는다.
     */
    @Schema(name = "ShopPurchaseResult", description = "아이템 구매 결과")
    @Builder
    public record PurchaseResult(
            @Schema(description = "이번에 산 아이템 id. 요청한 순서 그대로다")
            List<Long> purchasedItemIds,
            @Schema(description = "재화별 결제 내역. 한 재화로만 샀으면 한 줄이다")
            List<Payment> payments
    ) {
    }

    /**
     * 한 재화가 얼마나 모자란지.
     *
     * <p>"부족합니다" 만으로는 사용자가 무엇을 해야 하는지 알 수 없다 — <b>몇 개를 더 채우면
     * 되는지</b>를 알아야 충전하러 갈지 아이템을 뺄지 정할 수 있다.
     */
    @Schema(name = "ShopCurrencyShortage", description = "재화 한 종류의 부족분")
    @Builder
    public record Shortage(
            @Schema(description = "모자란 재화") Currency currency,
            @Schema(description = "이 재화로 내야 하는 총액") int required,
            @Schema(description = "지금 가진 수량") int balance,
            @Schema(description = "더 필요한 수량") int shortfall
    ) {
    }

    /**
     * 잔액 부족 안내. <b>실패 응답의 본문으로 나간다.</b>
     *
     * <p><b>모자란 재화를 전부 싣는다.</b> 하나씩 알려주면 사용자가 충전하고 돌아왔을 때 다른
     * 재화로 또 막힌다 — 그래서 차감을 시작하기 전에 모든 재화를 먼저 검사한다.
     */
    @Schema(name = "ShopPurchaseShortage", description = "구매 재화 부족 안내")
    @Builder
    public record PurchaseShortage(
            @Schema(description = "모자란 재화 목록") List<Shortage> shortages
    ) {
    }

    /**
     * 살 수 없는 아이템 안내. <b>실패 응답의 본문으로 나간다.</b>
     *
     * <p><b>어떤 아이템이 막혔는지</b>를 알아야 화면에서 그것만 빼고 다시 시도할 수 있다.
     * 목록 전체를 거절당하면 사용자는 무엇을 지워야 할지 알 수 없다.
     */
    @Schema(name = "ShopRejectedItems", description = "구매할 수 없는 아이템 안내")
    @Builder
    public record RejectedItems(
            @Schema(description = "막힌 아이템 id. 사유는 응답 code 가 가른다")
            List<Long> itemIds
    ) {
    }
}
