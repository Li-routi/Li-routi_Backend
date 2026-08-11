package com.lirouti.domain.shop.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ShopReqDTO {

    private ShopReqDTO() {
    }

    /**
     * 착용 저장. <b>보낸 것이 곧 전체 착장이다.</b>
     *
     * <p>목록에 없는 슬롯은 벗는다. 그래야 벗기기를 위한 별도 표현이 필요 없다 — 부분 갱신으로
     * 두면 "안 보냈다" 와 "비우라고 보냈다" 를 가르는 규칙이 하나 더 생긴다.
     *
     * <p>슬롯은 받지 않는다. 아이템이 갖고 있으므로 서버가 알아낸다.
     */
    public record EquipAvatar(
            @NotNull(message = "착용할 아이템 목록은 필수입니다.")
            @Size(max = 20, message = "한 번에 착용할 수 있는 아이템이 너무 많습니다.")
            List<@NotNull(message = "아이템 id 는 비어 있을 수 없습니다.") Long> itemIds
    ) {
    }
}
