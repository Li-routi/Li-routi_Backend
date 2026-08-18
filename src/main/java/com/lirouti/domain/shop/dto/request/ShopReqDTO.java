package com.lirouti.domain.shop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ShopReqDTO {

    private ShopReqDTO() {
    }

    /**
     * 아이템 구매. <b>고른 것을 한 번에 사고, 하나라도 막히면 전부 안 산다.</b>
     *
     * <p>가격과 결제 재화는 받지 않는다 — 마스터가 갖고 있으므로 <b>클라이언트가 보낸 값을 믿지
     * 않는다.</b>
     *
     * <p><b>재화가 섞여도 된다.</b> 파란 보석 아이템과 주황 보석 아이템을 함께 담으면 재화별로
     * 나눠 차감한다. 아이템을 한 재화로 통일해야만 살 수 있는 제약을 두지 않는다.
     *
     * @param idempotencyKey 이 구매 하나를 가리키는 값. <b>클라이언트가 만든다</b> — "이 사용자
     *                       동작 하나" 를 아는 것은 클라이언트뿐이다. 재시도는 같은 키로, 새
     *                       구매는 새 키로 보낸다.
     *                       <p>중복 결제를 최종적으로 막는 것은 이 키가 아니라 <b>보유 유니크
     *                       제약</b>이다 — 아바타 아이템은 영구 보유라, 이미 산 것을 다시 사려
     *                       하면 차감에 닿기 전에 거절된다. 이 키는 그 위에서 원장 기록을
     *                       멱등하게 만들고, 어느 요청이 만든 거래인지 되짚을 수 있게 한다
     */
    public record Purchase(
            @NotEmpty(message = "구매할 아이템을 하나 이상 선택해야 합니다.")
            @Size(max = 30, message = "한 번에 구매할 수 있는 아이템이 너무 많습니다.")
            List<@NotNull(message = "아이템 id 는 비어 있을 수 없습니다.") Long> itemIds,

            @NotBlank(message = "멱등 키는 필수입니다.")
            @Size(max = 64, message = "멱등 키는 64자를 넘을 수 없습니다.")
            String idempotencyKey
    ) {
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
