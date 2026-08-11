package com.lirouti.domain.charge.dto.request;

import jakarta.validation.constraints.NotNull;

public final class ChargeReqDTO {

    private ChargeReqDTO() {
    }

    /** 결제 시작. 상품 id 하나면 된다 — 금액은 서버가 갖고 있다. */
    public record StartCharge(
            @NotNull(message = "상품 id 는 필수입니다.")
            Long productId
    ) {
    }

    /** 재화 교환. 비율은 상품이 갖는다. */
    public record Exchange(
            @NotNull(message = "상품 id 는 필수입니다.")
            Long productId
    ) {
    }
}
